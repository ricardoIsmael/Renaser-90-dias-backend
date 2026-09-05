package com.renaser.os.habits.application.ports.in.aviso;

import com.renaser.os.shared.domain.UserId;

/**
 * Recalcula los avisos automaticos que le corresponden AHORA a un aprendiz y publica uno
 * {@code AvisoHabitoDebidoEvent} por cada uno.
 *
 * <p><b>Por participante y no por lote, a proposito</b> (regla 02 §4): el barrido que lo llama
 * itera el padron y le da a cada participante su propia transaccion corta, de modo que uno con
 * datos rotos — una zona horaria invalida, por ejemplo — no puede tumbar el aviso de los demas
 * ni revertir lo ya publicado.
 *
 * <p><b>Idempotente por construccion.</b> No lleva ningun estado propio: dos corridas seguidas
 * producen el mismo resultado porque los avisos se derivan del calendario y la deduplicacion
 * final la hace {@code notificaciones} por {@code origen_evento_id} (C-7/V16). Correrlo dos
 * veces no molesta a nadie; correrlo tarde simplemente pierde la franja, sin dejar el sistema
 * desalineado.
 *
 * @return cuantos avisos se publicaron (0 es lo normal la enorme mayoria de las corridas)
 */
public interface DespacharAvisosHabitoUseCase {

    int despacharDe(UserId participanteId);
}
