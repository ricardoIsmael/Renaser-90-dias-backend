package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.mentoring.api.RojoDelDiaEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.ExtrasDelPush;
import com.renaser.os.notifications.application.ports.in.notificacion.SuperarNotificacionesUseCase;
import com.renaser.os.notifications.domain.model.notificacion.EntregaPush;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Convierte «un aprendiz de tu grupo está en rojo hoy» (spec 057) en una entrada de la bandeja del mentor y en un push con dos botones,
 * «Escribirle» y «Ver ficha» (boceto D, FR-035).
 *
 * <p>La deduplicación no la hace este listener: {@code claveDeduplicacion} (mentor, aprendiz y semana: FR-032) viaja como {@code origenEventoId}
 * y el índice único de {@code notificaciones} rechaza la segunda; eso cubre además la reentrega del outbox. Si el mentor apagó «Aprendices en
 * rojo», {@code emitir} no crea la fila ni empuja nada (FR-030).
 *
 * <p>La clave cambia cada semana: el aviso de la semana anterior del mismo aprendiz que siga sin leer se supera (queda leído) al emitir el
 * nuevo, para que la bandeja no apile un «está en rojo» por semana de la misma persona (regla de los oyentes con clave, E-597).
 *
 * <p>El push lleva {@code categoryId = rojo-del-dia} y, en {@code data}, el {@code grupoId} y el {@code aprendizId} además de {@code route}:
 * la app los usa para abrir la conversación 1 a 1. Una app sin la categoría registrada ignora los botones y abre la ficha por {@code route}.
 */
@Component
class RojoDelDiaNotificationListener {

    /** La categoría de acciones que la app registra al arrancar ({@code setNotificationCategoryAsync}). */
    static final String CATEGORIA = "rojo-del-dia";

    /** Patrón {@code LIKE} del cuerpo de este aviso; va junto a {@link #cuerpo}: si el texto cambia, cambia con él. */
    static final String PATRON_DEL_CUERPO = "Lleva % de sus hábitos.%";

    private final EmitirNotificacionUseCase emitirNotificacionUseCase;
    private final SuperarNotificacionesUseCase superarNotificacionesUseCase;

    RojoDelDiaNotificationListener(EmitirNotificacionUseCase emitirNotificacionUseCase,
                                   SuperarNotificacionesUseCase superarNotificacionesUseCase) {
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
        this.superarNotificacionesUseCase = superarNotificacionesUseCase;
    }

    @ApplicationModuleListener
    void on(RojoDelDiaEvent event) {
        UserId mentor = UserId.of(event.mentorId());
        emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(
                mentor, TipoNotificacion.ROJO_DEL_DIA, titulo(event), cuerpo(event), event.rutaApp(),
                event.claveDeduplicacion()),
                EntregaPush.TODOS, null, null,
                new ExtrasDelPush(CATEGORIA, Map.of("grupoId", event.grupoId().toString(), "aprendizId", event.aprendizId().toString())))
                .ifPresent(nueva -> superarNotificacionesUseCase.superarAnteriores(mentor, TipoNotificacion.ROJO_DEL_DIA,
                        event.rutaApp(), PATRON_DEL_CUERPO, nueva.id()));
    }

    static String titulo(RojoDelDiaEvent event) {
        return event.nombreDelAprendiz() + " está en rojo hoy";
    }

    static String cuerpo(RojoDelDiaEvent event) {
        return "Lleva " + event.porcentaje() + " % de sus hábitos. Toca para ver su ficha y escribirle.";
    }
}
