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
 * <p>{@code eventoId}/{@code recordatorioId} son {@code UUID}/{@code Long} planos, no los
 * value objects de {@code calendar.domain} (paquete interno sin {@code @NamedInterface}) —
 * mismo criterio documentado en {@code RocaCompletadaEvent}.
 *
 * <p><b>Consumidor (D-182, 2026-09-26).</b> Hasta esa fecha NADIE escuchaba este evento, y como
 * {@code despachar()} marca la fila enviada en la misma transaccion en que lo publica, todos los
 * recordatorios de eventos se perdian en silencio (E-301). Lo consume
 * {@code notifications.RecordatorioEventoNotificationListener}.
 *
 * @param recordatorioId id de la fila de {@code recordatorios_evento}; es la clave de deduplicacion
 *                       de la notificacion (una por fila, aunque el outbox reentregue)
 * @param zonaHoraria    zona del evento ({@code eventos.timezone}), para que el texto diga la hora
 *                       de pared correcta ("hoy a las 05:30") y no la hora UTC. Agregada con D-182:
 *                       no hay publicaciones viejas de este evento en el outbox, porque sin
 *                       consumidor Modulith no registraba ninguna
 */
public record RecordatorioEventoDebidoEvent(Long recordatorioId, UUID eventoId, UserId destinatarioId,
                                             Instant inicioOcurrencia, String tituloEvento, boolean esAnuncio,
                                             String zonaHoraria, Instant occurredAt) implements DomainEvent {
}
