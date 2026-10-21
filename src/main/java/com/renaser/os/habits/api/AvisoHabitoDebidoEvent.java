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
 * <p><b>Entrega push (2026-09-08):</b> el aviso genera la notificacion REAL y visible dentro de
 * la app (fila en {@code notificaciones}) y tambien puede entregarse por Web Push si la persona
 * registro su navegador. {@code habits} solo publica este evento: no conoce VAPID, navegadores ni
 * proveedores externos.
 *
 * @param tipoAviso        espejo de {@code TipoAvisoHabito} como String, por el mismo motivo que
 *                         {@code HabitoDelDiaResumen.estado}: no filtrar un tipo interno de
 *                         `habits` fuera de su {@code @NamedInterface}
 * @param minutosQueFaltan cuanto falta para el momento avisado, ya redondeado hacia arriba — es
 *                         el numero que iba en el texto ("empieza en 15 minutos"). Desde la spec 007 el
 *                         texto dice la hora ({@code momentoAvisado}); los minutos quedan para redactar
 *                         las publicaciones viejas que el outbox reentregue
 * @param puntosEnJuego    lo que el aprendiz gana si lo entrega ahora. Va en el evento y no lo
 *                         recalcula `notifications` porque el puntaje es una regla de `habits`
 *                         (D-97) y solo este modulo la conoce
 * @param claveEvento      clave DETERMINISTICA de deduplicacion — el {@code origen_evento_id} de
 *                         {@code notificaciones} (C-7, V16). Es lo que hace que este flujo no
 *                         necesite una cola propia: el mismo aviso recalculado cinco minutos
 *                         despues produce la misma clave y la segunda emision se descarta sola
 * @param recordatorioActivo  {@code preferencias_horario.recordatorio_activo} de ese habito, o
 *                            {@code null} si el aprendiz nunca le configuro horario. Es un DATO, no
 *                            una orden: como se entrega (con o sin push, a que dispositivos) lo
 *                            decide `notifications` (D-184). Tambien es {@code null} en las
 *                            publicaciones anteriores a D-184 que el outbox reentregue
 * @param minutosRecordatorio {@code preferencias_horario.minutos_recordatorio}: con cuantos minutos
 *                            de antelacion suena la alarma LOCAL que la app programo en el telefono
 *                            ({@code null} = la app no programo ninguna)
 * @param habitoId            el habito (no el registro del dia): con esto la app abre Training en ese
 *                            habito al tocar el aviso (D-218). {@code null} en publicaciones anteriores a
 *                            D-218 que el outbox reentregue
 * @param categoriaHabito     la categoria del catalogo del habito ({@code BODY}, {@code MIND},
 *                            {@code CONSCIENCE}, {@code SPIRIT}…), que es la dimension que la app abre.
 *                            Tambien {@code null} en publicaciones viejas
 * @param momentoAvisado      el instante al que se refiere el aviso: la hora de disparo del habito (inicio) o
 *                            su plazo de entrega (vencimiento), calculado con la hora del habito y NO con el
 *                            reloj del barrido (spec 007, FR-012). Con el se escribe «te toca a las 07:00» y se
 *                            descarta un aviso que llega tarde. {@code null} en publicaciones anteriores a la
 *                            spec 007 que el outbox reentregue: esas se redactan con los minutos de siempre
 * @param zonaHoraria         la zona del participante ({@code America/Lima}) en que se dice la hora de
 *                            {@code momentoAvisado}. {@code null} en publicaciones viejas, por lo mismo
 * @param diaDeLaSemanaDelAviso el dia de la semana ISO (1 = lunes ... 7 = domingo) de la fecha del registro, en la
 *                            zona del participante, SOLO si el habito usa el esquema por dia (tiene alguna fila en
 *                            {@code horario_semanal_habito}: hora propia, aviso propio o dia apagado); {@code null}
 *                            si no. Es lo que le permite a `notifications` poner al push la misma etiqueta que la
 *                            app le pone a la alarma local de ese dia ({@code habito-{id}-d{iso}-0}), para que el
 *                            telefono reemplace y no apile los dos avisos (spec 008, R6). {@code null} tambien en
 *                            publicaciones anteriores a la spec 008 que el outbox reentregue
 */
public record AvisoHabitoDebidoEvent(UUID registroId, UserId participanteId, String tituloHabito, String tipoAviso,
                                      long minutosQueFaltan, int puntosEnJuego, UUID claveEvento,
                                      Boolean recordatorioActivo, Integer minutosRecordatorio,
                                      Instant occurredAt, UUID habitoId, String categoriaHabito,
                                      Instant momentoAvisado, String zonaHoraria, Integer diaDeLaSemanaDelAviso)
        implements DomainEvent {
}
