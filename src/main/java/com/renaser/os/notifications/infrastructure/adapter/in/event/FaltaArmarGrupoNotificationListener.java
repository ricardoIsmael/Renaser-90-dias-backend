package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.community.api.FaltaArmarGrupoEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Le pone en la bandeja al staff que falta armar un grupo (D-240). Mismo patrón que
 * {@link GrupoPorVencerNotificationListener}: a TODOS los que tienen el rol, y el índice único
 * (usuario, tipo, {@code origenEventoId}) le entrega UNO a cada uno aunque el evento se detecte cada
 * hora o el outbox lo reentregue.
 *
 * <p>Quién recibe qué:
 * <ul>
 *   <li>Sin grupo en curso para el traslado: administrador, alquimista y líder de mentores. Crear el
 *       grupo es del administrador; el líder reparte mentores y tiene que saber que hay gente
 *       esperando.</li>
 *   <li>Grupo en curso sin mentor: el líder de mentores, que es quien lo resuelve (pedido del dueño,
 *       2026-10-01).</li>
 * </ul>
 */
@Component
class FaltaArmarGrupoNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(FaltaArmarGrupoNotificationListener.class);

    private static final Set<UserRole> SIN_GRUPO_EN_CURSO =
            Set.of(UserRole.ADMIN, UserRole.ALCHEMIST, UserRole.MENTOR_LEAD);
    private static final Set<UserRole> GRUPO_SIN_MENTOR = Set.of(UserRole.MENTOR_LEAD);

    private final EmitirNotificacionUseCase emitirNotificacionUseCase;
    private final ParticipacionProgramaFinder participacionFinder;

    FaltaArmarGrupoNotificationListener(EmitirNotificacionUseCase emitirNotificacionUseCase,
                                         ParticipacionProgramaFinder participacionFinder) {
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
        this.participacionFinder = participacionFinder;
    }

    @ApplicationModuleListener
    void on(FaltaArmarGrupoEvent event) {
        List<UserId> destinatarios = participacionFinder.usuariosActivosConRol(rolesPara(event.motivo()));
        if (destinatarios.isEmpty()) {
            // No es normal: nadie puede resolverlo. WARN para que se vea en vez de perderse en silencio.
            log.warn("[notifications.FaltaArmarGrupoNotificationListener] {} y no hay staff activo a quien "
                    + "avisarle: {}", event.motivo(), event.cuerpo());
            return;
        }
        for (UserId destinatario : destinatarios) {
            emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(destinatario,
                    TipoNotificacion.ARMADO_DE_GRUPOS, event.titulo(), event.cuerpo(), event.rutaApp(),
                    event.claveDeduplicacion()));
        }
    }

    static Set<UserRole> rolesPara(FaltaArmarGrupoEvent.Motivo motivo) {
        return motivo == FaltaArmarGrupoEvent.Motivo.GRUPO_SIN_MENTOR ? GRUPO_SIN_MENTOR : SIN_GRUPO_EN_CURSO;
    }
}
