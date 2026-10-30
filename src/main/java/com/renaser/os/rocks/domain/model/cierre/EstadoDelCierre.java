package com.renaser.os.rocks.domain.model.cierre;

import java.time.LocalDate;

/**
 * En qué estado está el cierre de un día (spec 017, FR-005). Un día sin cerrar no se penaliza: solo se dice.
 */
public enum EstadoDelCierre {
    /** Es hoy y todavía no se cerró: se puede cerrar. */
    ABIERTO,
    /** La persona lo cerró (existe la marca del día). */
    CERRADO,
    /** El día ya terminó y nadie lo cerró. No se puede cerrar tarde. */
    SIN_CERRAR;

    /** {@code hoy} es la fecha en la zona de la persona. Una fecha futura no llega acá: la rechaza quien la recibe. */
    public static EstadoDelCierre de(LocalDate fecha, LocalDate hoy, boolean hayMarca) {
        if (hayMarca) {
            return CERRADO;
        }
        return fecha.isBefore(hoy) ? SIN_CERRAR : ABIERTO;
    }
}
