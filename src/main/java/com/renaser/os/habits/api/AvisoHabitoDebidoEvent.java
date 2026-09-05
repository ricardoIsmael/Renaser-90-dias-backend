package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Publicado cuando a un aprendiz le corresponde recibir uno de los dos avisos automaticos de
 * un habito: que esta por empezar, o que esta por vencer (pedido del dueno, 2026-09-05).
 *
 * <p>Mismo reparto de responsabilidades que {@code calendar.RecordatorioEventoDebidoEvent}:
 * `habits` decide QUE avisar y CUANDO, `notifications` decide COMO se entrega. Este modulo no
 * conoce la bandeja ni el push.
 *
 * <p><b>Estado actual del push (decision del dueno, 2026-09-05):</b> el aviso genera la
 * notificacion REAL y visible dentro de la app (fila en {@code notificaciones}); el push al
 * telefono NO. {@code PushPort} solo tiene {@code NoOpPushAdapter} — no hay FCM ni Expo
 * conectado, y conectarlo queda explicitamente fuera de alcance. El dia que exista un adaptador
 * real, este evento ya llega al lugar correcto y no hay que tocar `habits`.
 *
 * @param tipoAviso        espejo de {@code TipoAvisoHabito} como String, por el mismo motivo que
 *                         {@code HabitoDelDiaResumen.estado}: no filtrar un tipo interno de
 *                         `habits` fuera de su {@code @NamedInterface}
 * @param minutosQueFaltan cuanto falta para el momento avisado, ya redondeado hacia arriba — es
 *                         el numero que va en el texto ("empieza en 15 minutos")
 * @param puntosEnJuego    lo que el aprendiz gana si lo entrega ahora. Va en el evento y no lo
 *                         recalcula `notifications` porque el puntaje es una regla de `habits`
 *                         (D-97) y solo este modulo la conoce
 * @param claveEvento      clave DETERMINISTICA de deduplicacion — el {@code origen_evento_id} de
 *                         {@code notificaciones} (C-7, V16). Es lo que hace que este flujo no
 *                         necesite una cola propia: el mismo aviso recalculado cinco minutos
 *                         despues produce la misma clave y la segunda emision se descarta sola
 */
public record AvisoHabitoDebidoEvent(UUID registroId, UserId participanteId, String tituloHabito, String tipoAviso,
                                      long minutosQueFaltan, int puntosEnJuego, UUID claveEvento,
                                      Instant occurredAt) implements DomainEvent {
}
