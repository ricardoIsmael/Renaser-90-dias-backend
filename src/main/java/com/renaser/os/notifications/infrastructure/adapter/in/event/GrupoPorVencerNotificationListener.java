package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.community.api.GrupoPorVencerEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Le pone en la bandeja a cada administrador que a un grupo se le acaba el periodo.
 *
 * <p><b>Va a TODOS los administradores, no a uno.</b> No hay un "dueño" de un grupo en el modelo:
 * cualquiera con permiso puede reprogramarlo, y mandárselo solo al primero de la lista dejaría el
 * aviso dependiendo de quién estuviera de vacaciones.
 *
 * <p>Que lo reciban varios no multiplica el aviso para nadie: el índice único de
 * {@code notificaciones} es por (usuario, tipo, {@code origenEventoId}), así que la misma clave
 * de deduplicación le entrega UNA a cada uno. Eso cubre además la reentrega del outbox de
 * Modulith, que es at-least-once.
 *
 * <p>ALCHEMIST entra junto a ADMIN porque en este sistema es un administrador con más permisos,
 * no un rol aparte: en todos los guards aparecen juntos.
 */
@Component
class GrupoPorVencerNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(GrupoPorVencerNotificationListener.class);

    private static final Set<UserRole> ADMINISTRADORES = Set.of(UserRole.ADMIN, UserRole.ALCHEMIST);

    private final EmitirNotificacionUseCase emitirNotificacionUseCase;
    private final ParticipacionProgramaFinder participacionFinder;

    GrupoPorVencerNotificationListener(EmitirNotificacionUseCase emitirNotificacionUseCase,
                                        ParticipacionProgramaFinder participacionFinder) {
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
        this.participacionFinder = participacionFinder;
    }

    @ApplicationModuleListener
    void on(GrupoPorVencerEvent event) {
        List<UserId> administradores = participacionFinder.usuariosActivosConRol(ADMINISTRADORES);
        if (administradores.isEmpty()) {
            /* Sin administradores activos no hay a quién avisarle, y eso NO es normal: significa
               que los grupos se van a cerrar sin que nadie pueda reprogramarlos. Se registra en
               WARN para que aparezca, en vez de terminar en silencio como si no hubiera pasado
               nada. */
            log.warn("[notifications.GrupoPorVencerNotificationListener] el grupo {} vence en {} dia(s) "
                            + "y no hay ningun administrador activo a quien avisarle",
                    event.nombreDelGrupo(), event.diasRestantes());
            return;
        }
        for (UserId administrador : administradores) {
            emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(
                    administrador, TipoNotificacion.GRUPO_POR_VENCER,
                    "Un grupo esta por cerrar", cuerpo(event), event.rutaApp(), event.claveDeduplicacion()));
        }
    }

    private static String cuerpo(GrupoPorVencerEvent event) {
        return event.diasRestantes() == 1
                ? "El grupo " + event.nombreDelGrupo() + " termina hoy. Programa el siguiente o mueve a sus alumnos."
                : "Al grupo " + event.nombreDelGrupo() + " le quedan " + event.diasRestantes()
                        + " dias. Programa el siguiente o mueve a sus alumnos.";
    }
}
