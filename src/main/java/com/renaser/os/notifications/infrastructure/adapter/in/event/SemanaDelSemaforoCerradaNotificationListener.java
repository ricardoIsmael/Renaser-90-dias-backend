package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.notifications.domain.model.semaforo.AvisoRedactado;
import com.renaser.os.notifications.domain.model.semaforo.RedaccionDelSemaforo;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.SemanaDelSemaforoCerradaEvent;
import com.renaser.os.shared.domain.UserId;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Le avisa a cada persona medida que su semana del semáforo ya cerró (D-168,
 * docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §1.2): bandeja y push, tipo {@code RESUMEN_SEMANAL}.
 *
 * <p><b>Sin cifras ni colores.</b> El título y el cuerpo son también el texto del push
 * ({@code NotificacionService.intentarPush} los manda tal cual), y el push de la app no lleva
 * métricas: el resultado se ve adentro, en {@code /semaforo}. Del evento solo se mira si la semana
 * tuvo algo programado, para elegir el caso ({@link RedaccionDelSemaforo}); nunca el número ni el
 * color. Quien sí los cuenta es el acompañante, dentro del chat ({@code rag}).
 *
 * <p>La deduplicación no la hace este listener: {@code claveDeduplicacion} (una por persona y
 * semana) viaja como {@code origenEventoId} y el índice único de {@code notificaciones} rechaza la
 * segunda. Eso cubre además la reentrega del outbox de Modulith, que es at-least-once.
 */
@Component
class SemanaDelSemaforoCerradaNotificationListener {

    private final EmitirNotificacionUseCase emitirNotificacionUseCase;

    SemanaDelSemaforoCerradaNotificationListener(EmitirNotificacionUseCase emitirNotificacionUseCase) {
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
    }

    @ApplicationModuleListener
    void on(SemanaDelSemaforoCerradaEvent event) {
        AvisoRedactado aviso = RedaccionDelSemaforo.paraLaPersona(tuvoAlgoProgramado(event));
        emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(
                UserId.of(event.participanteId()), TipoNotificacion.RESUMEN_SEMANAL, aviso.titulo(),
                aviso.cuerpo(), event.rutaApp(), event.claveDeduplicacion()));
    }

    /** Por contrato, sin porcentaje = ningún día tuvo algo programado (§3.3). */
    private static boolean tuvoAlgoProgramado(SemanaDelSemaforoCerradaEvent event) {
        return event.porcentaje() != null && event.color() != ColorSemaforo.SIN_DATOS;
    }
}
