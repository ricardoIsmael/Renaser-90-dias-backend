package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.mentoring.api.AvisoDeAcompanamientoEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.application.ports.in.notificacion.SuperarNotificacionesUseCase;
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
 * <p><b>Un solo aviso vigente por (mentor, aprendiz, motivo) (issue #165).</b> La clave de deduplicación
 * identifica el episodio, pero el episodio se mueve (el ancla de «sin actividad» es la obligación más vieja
 * de una ventana de 45 días que avanza; la de «evidencia vencida», la más vieja sin entregar), así que cada
 * vez que se mueve nace otro aviso y los anteriores se quedaban sin leer con cifras viejas. Al nacer uno
 * nuevo, los sin leer de la misma ruta (aprendiz y grupo) y el mismo motivo quedan como leídos: la fila
 * sigue ahí como historia y la app, que ya oculta las leídas, no cambia. Si el nuevo fue una reentrega del
 * outbox (la fila ya existía), no se toca nada.
 *
 * <p>El texto nombra al alumno y dice qué incumplió —cuántas evidencias debe o cuántos días lleva
 * sin actividad—; el javadoc decía que no lo decía. Lo que sí se queda adentro de la app, contra un
 * endpoint que revalida permisos, es el detalle: si el mentor rotó entre el aviso y el toque, no
 * debería poder leer nada (plan.md §9). Y como ese mismo texto sale además por push, que el
 * destinatario siga teniendo cuenta vigente lo comprueba {@code NotificacionService.intentarPush}.
 */
@Component
class AvisoAcompanamientoNotificationListener {

    private static final String MOTIVO_EVIDENCIA_VENCIDA = "EVIDENCIA_VENCIDA";

    private final EmitirNotificacionUseCase emitirNotificacionUseCase;
    private final SuperarNotificacionesUseCase superarNotificacionesUseCase;

    AvisoAcompanamientoNotificationListener(EmitirNotificacionUseCase emitirNotificacionUseCase,
                                            SuperarNotificacionesUseCase superarNotificacionesUseCase) {
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
        this.superarNotificacionesUseCase = superarNotificacionesUseCase;
    }

    @ApplicationModuleListener
    void on(AvisoDeAcompanamientoEvent event) {
        UserId mentor = UserId.of(event.mentorId());
        emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(
                mentor, TipoNotificacion.ACOMPANAMIENTO_ALUMNO,
                "Novedades de acompañamiento", cuerpo(event), event.rutaApp(), event.claveDeduplicacion()))
                .ifPresent(nueva -> superarNotificacionesUseCase.superarAnteriores(mentor,
                        TipoNotificacion.ACOMPANAMIENTO_ALUMNO, event.rutaApp(), patronDelMotivo(event.motivo()),
                        nueva.id()));
    }

    /**
     * Patrón {@code LIKE} de los cuerpos del mismo motivo, sea cual sea el nombre y la cifra. Va junto a
     * {@link #cuerpo} a propósito: si el texto cambia, el patrón cambia con él (lo exige la prueba).
     * La migración V197 usa los mismos fragmentos para limpiar lo acumulado antes del arreglo.
     */
    static String patronDelMotivo(String motivo) {
        return MOTIVO_EVIDENCIA_VENCIDA.equals(motivo) ? "% evidencia%pendiente%." : "% no registra actividad hace %";
    }

    private static String cuerpo(AvisoDeAcompanamientoEvent event) {
        return MOTIVO_EVIDENCIA_VENCIDA.equals(event.motivo())
                ? event.nombreDelAlumno() + " tiene " + event.magnitud()
                        + (event.magnitud() == 1 ? " evidencia pendiente" : " evidencias pendientes") + "."
                : event.nombreDelAlumno() + " no registra actividad hace " + event.magnitud()
                        + (event.magnitud() == 1 ? " día." : " días.");
    }
}
