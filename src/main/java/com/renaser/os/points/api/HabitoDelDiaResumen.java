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
 * @param cuentaParaElDia si el track entra en el calculo del dia del semaforo (D-277): un opcional sin completar
 *               no cuenta, ni a favor ni en contra. Lo decide {@code habits}
 *               ({@code ConteoDiarioHabitos.contaba}), el dueno de la regla: quien lee esto no la copia
 */
public record HabitoDelDiaResumen(UUID trackId, String tituloHabito, String estado, boolean cuentaParaElDia) {

    /** Un track que cuenta: lo que era antes de D-277, cuando el resumen no decia si el track contaba. */
    public HabitoDelDiaResumen(UUID trackId, String tituloHabito, String estado) {
        this(trackId, tituloHabito, estado, true);
    }
}
