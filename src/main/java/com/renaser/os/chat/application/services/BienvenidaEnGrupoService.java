package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.DarBienvenidaEnGrupoUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.EnviarMensajeCommand;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.OrigenMedia;
import com.renaser.os.chat.application.ports.out.bienvenida.BienvenidaEnGrupoPort;
import com.renaser.os.chat.application.ports.out.bienvenida.BienvenidaEnGrupoPort.Pendiente;
import com.renaser.os.chat.application.ports.out.bienvenida.BienvenidaEnGrupoPort.Pendientes;
import com.renaser.os.chat.application.ports.out.bienvenida.TextosDeBienvenidaPort;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.PrimerNombre;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * La bienvenida del mentor en el chat del grupo estable (OPE-01-01, D-191): «un mensaje personalizado
 * de bienvenida reforzando pertenencia y compromiso», firmado por el mentor vigente del grupo, con el
 * texto {@code grupo} de {@code bienvenida/mensajes.yaml}. Sin tarjeta: la tarjeta es del soporte.
 *
 * <p><b>Idempotente por pertenencia.</b> Cada aprendiz, en su transacción: primero la marca
 * ({@code UPDATE … WHERE bienvenida_enviada_en IS NULL}) y, solo si esa llamada la dejó, el mensaje.
 * O quedan los dos o ninguno; una entrega cruzada espera el lock de la fila, la encuentra marcada y
 * no manda nada.
 *
 * <p><b>Lo que no es un fallo no se reintenta</b>: sin texto, sin chat del grupo, mentor o aprendiz
 * sin cuenta activa. La pertenencia queda pendiente y se revisa con el próximo cambio del grupo. Lo
 * demás se lanza al final, después de intentar a todos (mismo criterio que G-3).
 */
@Service
public class BienvenidaEnGrupoService implements DarBienvenidaEnGrupoUseCase {

    private static final Logger log = LoggerFactory.getLogger(BienvenidaEnGrupoService.class);

    private final BienvenidaEnGrupoPort bienvenidaPort;
    private final TextosDeBienvenidaPort textos;
    private final LoadConversacionPort loadConversacionPort;
    private final EnviarMensajeUseCase enviarMensaje;
    private final UserSummaryFinder userSummaryFinder;
    private final TransactionTemplate transaccionPropia;

    public BienvenidaEnGrupoService(BienvenidaEnGrupoPort bienvenidaPort, TextosDeBienvenidaPort textos,
                                    LoadConversacionPort loadConversacionPort, EnviarMensajeUseCase enviarMensaje,
                                    UserSummaryFinder userSummaryFinder, PlatformTransactionManager transactionManager) {
        this.bienvenidaPort = bienvenidaPort;
        this.textos = textos;
        this.loadConversacionPort = loadConversacionPort;
        this.enviarMensaje = enviarMensaje;
        this.userSummaryFinder = userSummaryFinder;
        this.transaccionPropia = new TransactionTemplate(transactionManager);
        this.transaccionPropia.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    @Override
    public int darBienvenidas(UUID celulaId) {
        if (textos.grupo().isEmpty()) {
            return 0;
        }
        Optional<Pendientes> pendientes = bienvenidaPort.pendientes(celulaId).filter(p -> !p.aprendices().isEmpty());
        if (pendientes.isEmpty()) {
            return 0;
        }
        Optional<ConversacionId> chat = loadConversacionPort.porCelulaId(celulaId).map(Conversacion::id);
        if (chat.isEmpty()) {
            log.warn("[chat.bienvenida-grupo] el grupo {} no tiene chat: la bienvenida queda pendiente", celulaId);
            return 0;
        }
        return darATodos(chat.get(), pendientes.get(), cuentasActivas(pendientes.get()));
    }

    private int darATodos(ConversacionId chat, Pendientes pendientes, Map<UserId, UserSummary> activas) {
        UserSummary mentor = activas.get(pendientes.mentorId());
        if (mentor == null) {
            log.warn("[chat.bienvenida-grupo] el mentor {} no tiene cuenta activa: la bienvenida queda pendiente",
                    pendientes.mentorId());
            return 0;
        }
        int dadas = 0;
        List<RuntimeException> fallos = new ArrayList<>();
        for (Pendiente pendiente : pendientes.aprendices()) {
            UserSummary aprendiz = activas.get(pendiente.aprendizId());
            if (aprendiz == null) {
                continue;
            }
            try {
                dadas += dar(chat, pendiente, mentor, aprendiz) ? 1 : 0;
            } catch (RuntimeException e) {
                log.warn("[chat.bienvenida-grupo] no se pudo dar la bienvenida a {}; se reintentara",
                        pendiente.aprendizId(), e);
                fallos.add(e);
            }
        }
        if (!fallos.isEmpty()) {
            throw new IllegalStateException("Fallaron " + fallos.size() + " bienvenidas de grupo en " + chat
                    + " (se dieron " + dadas + "); el outbox reintenta el evento", fallos.get(0));
        }
        return dadas;
    }

    /** Una sola lectura de cuentas para todo el grupo (anti-N+1), solo las que pueden entrar. */
    private Map<UserId, UserSummary> cuentasActivas(Pendientes pendientes) {
        List<UserId> ids = new ArrayList<>(pendientes.aprendices().stream().map(Pendiente::aprendizId).toList());
        ids.add(pendientes.mentorId());
        Map<UserId, UserSummary> cuentas = new HashMap<>(userSummaryFinder.findByIds(ids));
        cuentas.values().removeIf(c -> !c.status().allowsAccess());
        return cuentas;
    }

    /** La marca y el mensaje en una transacción. {@code false} si otra entrega ya la había dado. */
    private boolean dar(ConversacionId chat, Pendiente pendiente, UserSummary mentor, UserSummary aprendiz) {
        String texto = textos.grupo()
                .replace("{nombre}", PrimerNombre.de(aprendiz.fullName()))
                .replace("{mentor}", PrimerNombre.de(mentor.fullName()));
        return Boolean.TRUE.equals(transaccionPropia.execute(status -> {
            if (!bienvenidaPort.marcarDada(pendiente.asignacionId())) {
                return false;
            }
            enviarMensaje.enviar(new EnviarMensajeCommand(mentor.id(), chat, TipoMensaje.TEXTO, texto,
                    null, null, null, null, null, null, OrigenMedia.CLIENTE));
            return true;
        }));
    }
}
