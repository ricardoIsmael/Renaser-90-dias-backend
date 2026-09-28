package com.renaser.os.calendar.api;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Publicado cuando un recordatorio de la cola {@code recordatorios_evento} vence y debe
 * entregarse. Lo consume `notifications` (listener propio, fuera de este modulo) para
 * decidir el canal de entrega (push/email) — `calendar` solo decide QUE y CUANDO, nunca
 * COMO se entrega, mismo reparto de responsabilidades que {@code RocaCompletadaEvent}
 * (`rocks.api`) y {@code HabitoCompletadoEvent} (`habits.api`).
 *
 * <p>{@code esAnuncio} distingue el aviso "hay un evento nuevo" (clave fija
 * {@code sendAt = occurrenceStart = createdAt} del evento, ver
 * {@code GenerarRecordatoriosScheduler}) de un recordatorio real de una ocurrencia — mismo
 * criterio que {@code esAnuncio()} del repo viejo (reminderService.ts): un recordatorio de
 * verdad siempre nace con {@code sendAt} en el futuro respecto a la creacion del evento.
 *
 * <p><b>Corregido 2026-09-27 (E-361).</b> En un anuncio, {@code inicioOcurrencia} viajaba con la clave de
 * la cola, que es la hora de CREACION del evento, y el aviso decia «Es el sabado 26 de setiembre a las
 * 20:02» de una clase del domingo a las 19:00. Desde esta fecha lleva el inicio real: el del evento, o el de
 * su proxima ocurrencia si es una serie ({@code DespachoDeRecordatoriosService}). La clave de la cola no
 * cambia; la deduplicacion sigue siendo por {@code recordatorioId}.
 *
 * <p>{@code eventoId}/{@code recordatorioId} son {@code UUID}/{@code Long} planos, no los
 * value objects de {@code calendar.domain} (paquete interno sin {@code @NamedInterface}) —
 * mismo criterio documentado en {@code RocaCompletadaEvent}.
 *
 * <p><b>Consumidor (D-182, 2026-09-26).</b> Hasta esa fecha NADIE escuchaba este evento, y como
 * {@code despachar()} marca la fila enviada en la misma transaccion en que lo publica, todos los
 * recordatorios de eventos se perdian en silencio (E-301). Lo consume
 * {@code notifications.RecordatorioEventoNotificationListener}.
 *
 * <p><b>Corregido 2026-09-27 (E-360).</b> Aun con consumidor, ninguna publicacion de este evento llego a
 * {@code event_publication}: el despacho marcaba las filas enviadas despues de publicar, con un UPDATE que
 * vacia el contexto de persistencia y cancelaba los {@code persist} pendientes del outbox. El aviso que
 * fallaba al entregarse no tenia nada que reintentar.
 *
 * @param recordatorioId id de la fila de {@code recordatorios_evento}; es la clave de deduplicacion
 *                       de la notificacion (una por fila, aunque el outbox reentregue)
 * @param zonaHoraria    zona del evento ({@code eventos.timezone}), para que el texto diga la hora
 *                       de pared correcta ("hoy a las 05:30") y no la hora UTC. Agregada con D-182:
 *                       no hay publicaciones viejas de este evento en el outbox, porque sin
 *                       consumidor Modulith no registraba ninguna
 * @param asistenciaConfirmada la persona respondio "Voy" a ESTA ocurrencia, leido al despachar
 *                       (D-189). {@code calendar} ya no apaga los avisos al confirmar: la app del
 *                       telefono programa una alarma local, pero la web no, y solo {@code notifications}
 *                       sabe si la persona tiene un telefono registrado. Siempre {@code false} en un
 *                       anuncio. Es {@code Boolean} y no {@code boolean} a proposito: una
 *                       publicacion vieja del outbox (anterior a D-189) no trae el campo, y con
 *                       Jackson 3 un primitivo ausente puede fallar al leerse; llega {@code null}
 *                       y {@link #confirmoAsistencia()} lo trata como "no confirmo", el lado seguro
 */
public record RecordatorioEventoDebidoEvent(Long recordatorioId, UUID eventoId, UserId destinatarioId,
                                             Instant inicioOcurrencia, String tituloEvento, boolean esAnuncio,
                                             Boolean asistenciaConfirmada, String zonaHoraria,
                                             Instant occurredAt) implements DomainEvent {

    /** {@code true} solo si la persona dijo "Voy" a esta ocurrencia; {@code null} cuenta como no. */
    public boolean confirmoAsistencia() {
        return Boolean.TRUE.equals(asistenciaConfirmada);
    }
}
