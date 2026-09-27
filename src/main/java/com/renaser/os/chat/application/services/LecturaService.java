package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.lectura.AnunciarLecturaUseCase;
import com.renaser.os.chat.application.ports.in.lectura.ConsultarLecturaUseCase;
import com.renaser.os.chat.application.ports.out.lectura.MarcasDeLecturaPort;
import com.renaser.os.chat.application.ports.out.lectura.PublicarLecturaFanoutPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.Participante;
import com.renaser.os.chat.domain.model.mensaje.ConfirmacionDeLectura;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * La doble marca de leído (✓✓, D-208): hasta dónde leyeron todos en una conversación, y el aviso en
 * vivo cuando eso avanza.
 *
 * <p><b>Quiénes cuentan.</b> Los participantes de la conversación con la cuenta ACTIVA. Una cuenta
 * suspendida conserva su fila (suspender no toca el chat), pero no puede abrir la conversación: si
 * contara, su marca quedaría congelada y ningún mensaje de su grupo, o de ningún soporte si es del
 * staff, llegaría a ✓✓. En un soporte coincide con la regla 1 de D-136: adentro van el aprendiz y el
 * staff <i>activo</i>. El estado sale de {@code users.api} en una sola consulta por conversación.
 *
 * <p><b>Sin {@code @Transactional}.</b> Son dos lecturas (participantes y cuentas) y, al anunciar, un
 * aviso a Redis; ninguna necesita una conexión retenida entre las dos.
 */
@Service
public class LecturaService implements ConsultarLecturaUseCase, AnunciarLecturaUseCase {

    private static final Logger log = LoggerFactory.getLogger(LecturaService.class);

    private final MarcasDeLecturaPort marcasDeLecturaPort;
    private final PublicarLecturaFanoutPort publicarLecturaFanoutPort;
    private final UserSummaryFinder userSummaryFinder;

    public LecturaService(MarcasDeLecturaPort marcasDeLecturaPort, PublicarLecturaFanoutPort publicarLecturaFanoutPort,
                          UserSummaryFinder userSummaryFinder) {
        this.marcasDeLecturaPort = marcasDeLecturaPort;
        this.publicarLecturaFanoutPort = publicarLecturaFanoutPort;
        this.userSummaryFinder = userSummaryFinder;
    }

    /** En la comunidad devuelve la confirmación vacía sin consultar nada: ahí no hay ✓✓. */
    @Override
    public ConfirmacionDeLectura confirmacionDe(Conversacion conversacion) {
        if (!conversacion.confirmaLectura()) {
            return ConfirmacionDeLectura.sinDobleMarca();
        }
        List<Participante> participantes = marcasDeLecturaPort.participantesDe(conversacion.id());
        return ConfirmacionDeLectura.de(conversacion, conCuentaActiva(participantes));
    }

    /**
     * Sale solo si hay algo que decir: con la marca vacía (alguien no leyó nunca, o no queda nadie del
     * otro lado) no se publica. Tampoco se compara con la marca anterior para callar un aviso repetido:
     * haría falta otra consulta, y la app ya ignora una marca que no avanza.
     */
    @Override
    public void anunciar(Conversacion conversacion) {
        try {
            confirmacionDe(conversacion).leidoPorTodosHasta()
                    .ifPresent(hasta -> publicarLecturaFanoutPort.publicarLectura(conversacion.id(), hasta));
        } catch (RuntimeException e) {
            // La lectura ya está guardada: sin el aviso, el ✓✓ se ve al volver a abrir el chat.
            log.warn("No se pudo avisar en vivo la lectura de la conversacion {}", conversacion.id(), e);
        }
    }

    private List<Participante> conCuentaActiva(List<Participante> participantes) {
        if (participantes.isEmpty()) {
            return participantes;
        }
        Map<UserId, UserSummary> cuentas = userSummaryFinder.findByIds(
                participantes.stream().map(Participante::usuarioId).toList());
        return participantes.stream()
                .filter(participante -> {
                    UserSummary cuenta = cuentas.get(participante.usuarioId());
                    return cuenta != null && cuenta.status().allowsAccess();
                })
                .toList();
    }
}
