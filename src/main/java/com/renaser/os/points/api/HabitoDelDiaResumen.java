package com.renaser.os.points.api;

import java.util.UUID;

/**
 * Proyeccion minima de un track de habito de hoy, para pantallas de resumen (GET /home,
 * P-05) que solo necesitan saber cuantos/cuales hay y en que estado — no el catalogo
 * resuelto completo que ya expone {@code ConsultarTracksDelDiaConCatalogoUseCase} para la
 * pantalla de habitos propiamente dicha.
 *
 * @param estado espejo del enum interno {@code EstadoRegistro}, como String por el mismo
 *               motivo que {@code EntradaDiarioSummary.tipo}: no filtrar un tipo de dominio
 *               de `habits` fuera de su {@code @NamedInterface}
 */
public record HabitoDelDiaResumen(UUID trackId, String tituloHabito, String estado) {
}
