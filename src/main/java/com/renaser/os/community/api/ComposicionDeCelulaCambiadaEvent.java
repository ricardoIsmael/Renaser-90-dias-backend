package com.renaser.os.community.api;

import java.time.Instant;
import java.util.UUID;

/**
 * La composición de un grupo cambió: rotó su mentor, entró o salió un aprendiz.
 *
 * <p><b>No dice qué cambió, a propósito.</b> Quien lo escucha vuelve a pedir la lista completa
 * y reconcilia contra ella. Es la diferencia entre un evento de delta y uno de invalidación:
 * con deltas, un evento viejo reentregado fuera de orden reincorporaría al mentor saliente;
 * con este, la reconciliación siempre converge al estado actual, sin importar en qué orden
 * lleguen los avisos ni cuántas veces (plan.md §6).
 *
 * @param ocurridoEn cuándo se detectó el cambio. Informativo: la reconciliación consulta el
 *                   estado de AHORA, no el de este instante.
 */
public record ComposicionDeCelulaCambiadaEvent(UUID celulaId, Instant ocurridoEn) {
}
