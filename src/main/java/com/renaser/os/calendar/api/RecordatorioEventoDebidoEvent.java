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
 * @param avisoEnApp     si es un aviso del telefono (bandeja y push); {@code null} (una publicacion anterior a la spec 041) cuenta como
 *                       {@code true}. Con {@code false} la fila solo existe para el mensaje y {@code notifications} no emite el
 *                       aviso de la app (spec 041)
 * @param avisoDeMensajeMin cuantos minutos antes del inicio es este aviso de MENSAJE (1440, 60, 30, 10; chat y WhatsApp), o {@code null}
 *                       si la fila no es de mensaje (spec 041). {@code calendar} decide cuando; {@code notifications} decide a quien y por donde
 * @param formacion      la formacion del evento, para decir su nombre en el mensaje (spec 041); {@code null} = la Escuela
 * @param enlace         el enlace para entrar si el evento es en linea (Meet, Zoom u otro), leido del evento al despachar; {@code null} si no hay
 * @param lugar          la direccion si el evento es presencial; {@code null} si no
 * @param audienciaElegida el evento tiene audiencia elegida (spec 044); {@code null} en una publicación anterior
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
                                             Instant occurredAt, Boolean avisoEnApp, Integer avisoDeMensajeMin,
                                             String formacion, String enlace,
                                             String lugar, Boolean audienciaElegida) implements DomainEvent {

    /** Sin la marca de audiencia elegida (la forma de la spec 041; las publicaciones viejas del outbox la usan). */
    public RecordatorioEventoDebidoEvent(Long recordatorioId, UUID eventoId, UserId destinatarioId,
                                         Instant inicioOcurrencia, String tituloEvento, boolean esAnuncio,
                                         Boolean asistenciaConfirmada, String zonaHoraria, Instant occurredAt, Boolean avisoEnApp,
                                         Integer avisoDeMensajeMin, String formacion, String enlace, String lugar) {
        this(recordatorioId, eventoId, destinatarioId, inicioOcurrencia, tituloEvento, esAnuncio, asistenciaConfirmada, zonaHoraria,
                occurredAt, avisoEnApp, avisoDeMensajeMin, formacion, enlace, lugar, null);
    }

    /**
     * {@code true} si el evento tiene audiencia elegida (spec 044): su chat y su WhatsApp le llegan a todos los invitados que no dijeron «No voy»
     * y de cualquier rol (FR-021, FR-024). {@code null} (una publicación anterior) cuenta como no.
     */
    public boolean esDeAudienciaElegida() {
        return Boolean.TRUE.equals(audienciaElegida);
    }

    /** Un aviso del telefono, sin nada de mensaje (la forma de antes de la spec 041; las publicaciones viejas del outbox la usan). */
    public RecordatorioEventoDebidoEvent(Long recordatorioId, UUID eventoId, UserId destinatarioId,
                                         Instant inicioOcurrencia, String tituloEvento, boolean esAnuncio,
                                         Boolean asistenciaConfirmada, String zonaHoraria, Instant occurredAt) {
        this(recordatorioId, eventoId, destinatarioId, inicioOcurrencia, tituloEvento, esAnuncio,
                asistenciaConfirmada, zonaHoraria, occurredAt, null, null, null, null, null, null);
    }

    /** {@code true} salvo que la fila sea SOLO de mensaje; {@code null} (publicacion vieja) cuenta como aviso del telefono. */
    public boolean esAvisoEnApp() {
        return !Boolean.FALSE.equals(avisoEnApp);
    }

    /** {@code true} si esta fila tambien es un aviso de MENSAJE (chat y WhatsApp). */
    public boolean esAvisoDeMensaje() {
        return avisoDeMensajeMin != null && avisoDeMensajeMin > 0;
    }

    /** {@code true} solo si la persona dijo "Voy" a esta ocurrencia; {@code null} cuenta como no. */
    public boolean confirmoAsistencia() {
        return Boolean.TRUE.equals(asistenciaConfirmada);
    }
}
