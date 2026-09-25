package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.mentoring.api.ResumenSemanalGeneralEvent;
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
 * Le avisa al líder de mentores, a los administradores y a los alquimistas que la semana del
 * semáforo cerró para los grupos (D-168, docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §1.2): bandeja
 * y push, tipo {@code RESUMEN_SEMANAL}.
 *
 * <p><b>Va a TODOS los usuarios activos con esos roles</b>, por el mismo motivo que
 * {@link GrupoPorVencerNotificationListener}: no hay un dueño único del resumen, y mandárselo al
 * primero de la lista lo dejaría dependiendo de quién esté de vacaciones. Que lo reciban varios no
 * multiplica el aviso para nadie: el índice único de {@code notificaciones} es por (usuario, tipo,
 * {@code origenEventoId}), así que la clave de la semana le entrega UNA a cada uno.
 *
 * <p>Sin cifras y sin nombres: el push no lleva métricas y el líder ve el resumen por grupo sin
 * nombres de aprendices (RL-07 del SDD 002). El detalle está en {@code /semaforo/grupos}.
 */
@Component
class ResumenSemanalGeneralNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(ResumenSemanalGeneralNotificationListener.class);

    private static final Set<UserRole> DESTINATARIOS = Set.of(UserRole.MENTOR_LEAD, UserRole.ADMIN, UserRole.ALCHEMIST);

    /* TEXTOS PROVISORIOS (D-168): los aprueba el dueño, igual que los del acompañante (D-155). */
    static final String TITULO = "Semana cerrada";
    static final String CUERPO = "El semáforo de los grupos ya está listo.";

    private final EmitirNotificacionUseCase emitirNotificacionUseCase;
    private final ParticipacionProgramaFinder participacionFinder;

    ResumenSemanalGeneralNotificationListener(EmitirNotificacionUseCase emitirNotificacionUseCase,
                                              ParticipacionProgramaFinder participacionFinder) {
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
        this.participacionFinder = participacionFinder;
    }

    @ApplicationModuleListener
    void on(ResumenSemanalGeneralEvent event) {
        List<UserId> destinatarios = participacionFinder.usuariosActivosConRol(DESTINATARIOS);
        if (destinatarios.isEmpty()) {
            log.warn("[notifications.ResumenSemanalGeneralNotificationListener] cerro la semana {} del semaforo "
                    + "y no hay ningun lider, administrador ni alquimista activo a quien avisarle", event.hasta());
            return;
        }
        for (UserId destinatario : destinatarios) {
            emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(destinatario,
                    TipoNotificacion.RESUMEN_SEMANAL, TITULO, CUERPO, event.rutaApp(), event.claveDeduplicacion()));
        }
    }
}
