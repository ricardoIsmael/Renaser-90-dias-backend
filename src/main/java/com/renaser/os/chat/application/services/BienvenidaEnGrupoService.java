package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.DarBienvenidaEnGrupoUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase;
import com.renaser.os.chat.application.ports.out.bienvenida.BienvenidaEnGrupoPort;
import com.renaser.os.chat.application.ports.out.bienvenida.BienvenidaEnGrupoPort.Pendiente;
import com.renaser.os.chat.application.ports.out.bienvenida.BienvenidaEnGrupoPort.Pendientes;
import com.renaser.os.chat.application.ports.out.bienvenida.TextosDeBienvenidaPort;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.PrimerNombre;
import com.renaser.os.chat.domain.model.mensaje.ContenidoDelPrograma;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * La bienvenida en el chat del grupo estable (OPE-01-01, D-191): «un mensaje personalizado de
 * bienvenida reforzando pertenencia y compromiso», con el texto {@code grupo} vigente: el que guardó
 * Administración desde la app (D-210) o el original de {@code bienvenida/mensajes.yaml}. Sin tarjeta: la
 * tarjeta es del soporte.
 *
 * <p><b>La firma el programa, no el mentor</b> (D-204, 2026-09-27): sale como mensaje de SISTEMA
 * ({@link EnviarMensajeDelProgramaUseCase}), guardado a nombre del aprendiz al que se le da. El mentor
 * vigente sigue haciendo falta: el texto lo nombra ({@code {mentor}}) y sin él la bienvenida espera.
 * <blockquote><b>Corregido 2026-09-27 (D-204).</b> La firmaba el mentor vigente, como un mensaje
 * {@code TEXTO} suyo.</blockquote>
 *
 * <p><b>Idempotente por pertenencia.</b> Cada aprendiz, en su transacción: primero la marca
 * ({@code UPDATE … WHERE bienvenida_enviada_en IS NULL}) y, solo si esa llamada la dejó, el mensaje.
 * O quedan los dos o ninguno; una entrega cruzada espera el lock de la fila, la encuentra marcada y
 * no manda nada.
 *
 * <p><b>Lo que no es un fallo no se reintenta</b>: sin texto, sin chat del grupo, mentor o aprendiz
 * sin cuenta activa. La pertenencia queda pendiente y se revisa con el próximo cambio del grupo. Lo
 * demás se lanza al final, después de intentar a todos (mismo criterio que G-3).
 *
 * <p><b>Apagada salvo {@code BIENVENIDA_ACTIVA=true}</b> (D-204, 2026-09-27; el mismo interruptor
 * que la del soporte, D-199): los textos son borradores y no pueden llegar a aprendices reales hasta
 * que el dueño los apruebe. Apagada no consulta nada ni deja marca.
 *
 * <p><b>Sin bienvenidas atrasadas</b> (D-204). Como apagada no marca, las pertenencias abiertas
 * mientras estuvo apagada quedan sin marca; al prenderla, el primer cambio de cada grupo le daría la
 * bienvenida a quien lleva días (o semanas) ahí. Por eso solo la recibe quien entró al grupo hace
 * menos de {@link #VENTANA_PARA_DAR_LA_BIENVENIDA} ({@code asignaciones_celula.inicio}). Lo normal es
 * que salga segundos después de entrar; la ventana cubre además a un grupo que recibe su mentor al
 * día siguiente. Pasada la ventana la pertenencia queda sin marca y sin mensaje, a propósito.
 */
@Service
public class BienvenidaEnGrupoService implements DarBienvenidaEnGrupoUseCase {

    /**
     * Cuánto después de entrar al grupo todavía tiene sentido «Qué alegría que te sumes a este grupo»
     * (D-204). Decisión técnica a confirmar con el dueño: «hoy o ayer» sí, «hace días» no.
     */
    static final Duration VENTANA_PARA_DAR_LA_BIENVENIDA = Duration.ofHours(48);

    private static final Logger log = LoggerFactory.getLogger(BienvenidaEnGrupoService.class);

    private final BienvenidaEnGrupoPort bienvenidaPort;
    private final TextosDeBienvenidaPort textos;
    private final LoadConversacionPort loadConversacionPort;
    private final EnviarMensajeDelProgramaUseCase delPrograma;
    private final UserSummaryFinder userSummaryFinder;
    private final TransactionTemplate transaccionPropia;
    private final Clock clock;
    private final boolean activa;

    public BienvenidaEnGrupoService(BienvenidaEnGrupoPort bienvenidaPort, TextosDeBienvenidaPort textos,
                                    LoadConversacionPort loadConversacionPort, EnviarMensajeDelProgramaUseCase delPrograma,
                                    UserSummaryFinder userSummaryFinder, PlatformTransactionManager transactionManager,
                                    Clock clock, @Value("${renaser.chat.bienvenida.activa:false}") boolean activa) {
        this.bienvenidaPort = bienvenidaPort;
        this.textos = textos;
        this.loadConversacionPort = loadConversacionPort;
        this.delPrograma = delPrograma;
        this.userSummaryFinder = userSummaryFinder;
        this.transaccionPropia = new TransactionTemplate(transactionManager);
        this.transaccionPropia.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
        this.clock = clock;
        this.activa = activa;
    }

    @Override
    public int darBienvenidas(UUID celulaId) {
        if (!activa || textos.grupo().isEmpty()) {
            return 0;
        }
        Optional<Pendientes> pendientes = bienvenidaPort.pendientes(celulaId).map(this::soloQuienEntroHacePoco)
                .filter(p -> !p.aprendices().isEmpty());
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

    /** Deja afuera a quien entró al grupo antes de la ventana: sin bienvenidas atrasadas (D-204). */
    private Pendientes soloQuienEntroHacePoco(Pendientes pendientes) {
        Instant desde = clock.now().minus(VENTANA_PARA_DAR_LA_BIENVENIDA);
        List<Pendiente> recientes = pendientes.aprendices().stream()
                .filter(p -> !p.desde().isBefore(desde))
                .toList();
        if (recientes.size() < pendientes.aprendices().size()) {
            log.debug("[chat.bienvenida-grupo] {} pertenencias entraron hace más de {}: sin bienvenida atrasada",
                    pendientes.aprendices().size() - recientes.size(), VENTANA_PARA_DAR_LA_BIENVENIDA);
        }
        return new Pendientes(pendientes.mentorId(), recientes);
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
            delPrograma.enviarDelPrograma(chat, aprendiz.id(), ContenidoDelPrograma.texto(texto));
            return true;
        }));
    }
}
