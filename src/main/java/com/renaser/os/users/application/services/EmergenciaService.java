package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.EmergenciaPedidaEvent;
import com.renaser.os.users.api.EmergenciaResueltaEvent;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.application.ports.in.emergencia.AtenderEmergenciaUseCase;
import com.renaser.os.users.application.ports.in.emergencia.PedirAyudaPorEmergenciaUseCase;
import com.renaser.os.users.application.ports.in.emergencia.ResolverEmergenciaAlCambiarDiaUseCase;
import com.renaser.os.users.application.ports.out.emergencia.LoadSolicitudDeEmergenciaPort;
import com.renaser.os.users.application.ports.out.emergencia.SaveSolicitudDeEmergenciaPort;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.emergencia.SolicitudDeEmergencia;
import com.renaser.os.users.domain.model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * El botón de emergencia del aprendiz y su atención en soporte (D-244).
 *
 * <p><b>Una sola abierta por persona</b> (límite contra el spam: decidido acá a falta de una regla del
 * dueño, y dicho en el informe). Se revisa antes de guardar para responder un 409 con palabras, y el índice
 * único parcial de V90 cierra la carrera de dos toques a la vez.
 *
 * <p><b>Desde el Día 0</b> (respuesta del dueño del 2026-10-02): en el Día 0 el pedido es solo «necesito ayuda»,
 * sin día. Al resolverlo (cambiando el día o no) se publica {@link EmergenciaResueltaEvent} y {@code chat} le
 * escribe a la persona en su soporte.
 *
 * <p>El día actual se deriva en SU zona ({@code ParticipacionPrograma.diaVigente}), nunca con la fecha del
 * servidor: a las 02:00 UTC un aprendiz de Lima todavía vive el día de ayer (regla 02).
 */
@Service
public class EmergenciaService implements PedirAyudaPorEmergenciaUseCase, AtenderEmergenciaUseCase,
        ResolverEmergenciaAlCambiarDiaUseCase {

    private static final Logger log = LoggerFactory.getLogger(EmergenciaService.class);
    static final String YA_TIENE_UNA_ABIERTA =
            "Ya nos pediste ayuda. Soporte te va a escribir; si necesitas algo más, escríbele por el chat.";

    private final RequireActiveUserGuard requireActiveUserGuard;
    private final RequireAdminGuard requireAdminGuard;
    private final LoadUserPort loadUserPort;
    private final LoadParticipacionProgramaPort loadParticipacionPort;
    private final LoadSolicitudDeEmergenciaPort loadSolicitudPort;
    private final SaveSolicitudDeEmergenciaPort saveSolicitudPort;
    private final ApplicationEventPublisher eventos;
    private final IdGenerator idGenerator;
    private final Clock clock;

    EmergenciaService(RequireActiveUserGuard requireActiveUserGuard, RequireAdminGuard requireAdminGuard,
                      LoadUserPort loadUserPort, LoadParticipacionProgramaPort loadParticipacionPort,
                      LoadSolicitudDeEmergenciaPort loadSolicitudPort, SaveSolicitudDeEmergenciaPort saveSolicitudPort,
                      ApplicationEventPublisher eventos, IdGenerator idGenerator, Clock clock) {
        this.requireActiveUserGuard = requireActiveUserGuard;
        this.requireAdminGuard = requireAdminGuard;
        this.loadUserPort = loadUserPort;
        this.loadParticipacionPort = loadParticipacionPort;
        this.loadSolicitudPort = loadSolicitudPort;
        this.saveSolicitudPort = saveSolicitudPort;
        this.eventos = eventos;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    public MiEmergencia consultar(UserId actorId) {
        int diaActual = diaActualDe(requireAprendiz(actorId));
        return new MiEmergencia(diaActual, SolicitudDeEmergencia.diaMaximoPedible(diaActual),
                loadSolicitudPort.abiertaDe(actorId).orElse(null));
    }

    /** El evento va en la misma transacción que la fila: si no se guarda, no avisa; si avisa, quedó guardada. */
    @Override
    @Transactional
    public SolicitudDeEmergencia pedir(PedirAyudaCommand command) {
        int diaActual = diaActualDe(requireAprendiz(command.actorId()));
        if (loadSolicitudPort.abiertaDe(command.actorId()).isPresent()) {
            throw new IllegalStateException(YA_TIENE_UNA_ABIERTA);
        }
        SolicitudDeEmergencia solicitud = saveSolicitudPort.save(SolicitudDeEmergencia.pedir(idGenerator.newId(),
                command.actorId(), command.queOcurrio(), command.diaPedido(), diaActual, clock));
        eventos.publishEvent(new EmergenciaPedidaEvent(solicitud.id(), solicitud.aprendizId(), solicitud.queOcurrio(),
                solicitud.diaPedido(), solicitud.diaAlPedir()));
        log.info("[users.emergencia] {} pidió ayuda (día pedido {}) desde el día {}", command.actorId(),
                solicitud.diaPedido(), diaActual);
        return solicitud;
    }

    @Override
    public Optional<EmergenciaParaSoporte> abiertaDe(UserId actorId, UserId aprendizId) {
        requireAdminGuard.requireAdminActivo(actorId);
        return loadSolicitudPort.abiertaDe(aprendizId).map(solicitud -> new EmergenciaParaSoporte(solicitud,
                loadUserPort.byId(aprendizId).map(User::fullName).orElse(""),
                diaVigenteDe(aprendizId, solicitud.diaAlPedir())));
    }

    /** Recurso primero (404), gate de admin después (403), igual que «Cambiar día» (E-42). */
    @Override
    @Transactional
    public SolicitudDeEmergencia cerrarSinCambio(UserId actorId, UUID solicitudId) {
        SolicitudDeEmergencia solicitud = loadSolicitudPort.porId(solicitudId)
                .orElseThrow(() -> new NoSuchElementException("Pedido de emergencia no encontrado: " + solicitudId));
        requireAdminGuard.requireAdminActivo(actorId);
        solicitud.cerrarSinCambio(actorId, clock);
        SolicitudDeEmergencia cerrada = saveSolicitudPort.save(solicitud);
        avisarQueSeResolvio(cerrada, diaVigenteDe(cerrada.aprendizId(), cerrada.diaAlPedir()));
        return cerrada;
    }

    @Override
    public void alCambiarDia(UserId aprendizId, UserId actorId, int diaNuevo) {
        loadSolicitudPort.abiertaDe(aprendizId).ifPresent(solicitud -> {
            solicitud.resolverConCambioDeDia(actorId, diaNuevo, clock);
            saveSolicitudPort.save(solicitud);
            avisarQueSeResolvio(solicitud, diaNuevo);
            log.info("[users.emergencia] pedido {} resuelto: {} pasó al día {}", solicitud.id(), aprendizId, diaNuevo);
        });
    }

    /** En la transacción del cierre (outbox): si el cierre se deshace, el mensaje a la persona no sale. */
    private void avisarQueSeResolvio(SolicitudDeEmergencia solicitud, int diaActual) {
        eventos.publishEvent(new EmergenciaResueltaEvent(solicitud.id(), solicitud.aprendizId(), solicitud.diaAplicado(),
                diaActual));
    }

    private int diaVigenteDe(UserId aprendizId, int siNoHayFila) {
        return loadParticipacionPort.byParticipanteId(aprendizId).map(p -> p.diaVigente(clock)).orElse(siNoHayFila);
    }

    /** Solo un aprendiz activo: el staff con seguimiento personal no tiene a quién pedirle (su soporte no existe). */
    private User requireAprendiz(UserId actorId) {
        User actor = requireActiveUserGuard.of(actorId);
        if (actor.role() != UserRole.TRAINEE) {
            throw new NotAuthorizedException("El pedido de emergencia es para aprendices");
        }
        return actor;
    }

    private int diaActualDe(User aprendiz) {
        return loadParticipacionPort.byParticipanteId(aprendiz.id())
                .map(participacion -> participacion.diaVigente(clock))
                .orElse(0);
    }
}
