package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.mentoring.api.AvisoDeAcompanamientoEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Convierte un aviso de acompañamiento en una entrada de la bandeja del mentor.
 *
 * <p>La deduplicación no la hace este listener: {@code claveDeduplicacion} viaja como
 * {@code origenEventoId} y el índice único de {@code notificaciones} rechaza la segunda. Eso
 * cubre además la reentrega del outbox de Modulith, que es at-least-once.
 *
 * <p>El texto nombra al alumno pero no dice qué incumplió. El detalle se carga dentro de la app
 * contra un endpoint que revalida permisos: si el mentor rotó entre el aviso y el toque, no
 * debería poder leer nada (plan.md §9).
 */
@Component
class AvisoAcompanamientoNotificationListener {

    private final EmitirNotificacionUseCase emitirNotificacionUseCase;

    AvisoAcompanamientoNotificationListener(EmitirNotificacionUseCase emitirNotificacionUseCase) {
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
    }

    @ApplicationModuleListener
    void on(AvisoDeAcompanamientoEvent event) {
        emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(
                UserId.of(event.mentorId()), TipoNotificacion.ACOMPANAMIENTO_ALUMNO,
                "Novedades de acompañamiento", cuerpo(event), event.rutaApp(), event.claveDeduplicacion()));
    }

    private static String cuerpo(AvisoDeAcompanamientoEvent event) {
        return "EVIDENCIA_VENCIDA".equals(event.motivo())
                ? event.nombreDelAlumno() + " tiene " + event.magnitud()
                        + (event.magnitud() == 1 ? " evidencia pendiente" : " evidencias pendientes") + "."
                : event.nombreDelAlumno() + " no registra actividad hace " + event.magnitud()
                        + (event.magnitud() == 1 ? " día." : " días.");
    }
}
