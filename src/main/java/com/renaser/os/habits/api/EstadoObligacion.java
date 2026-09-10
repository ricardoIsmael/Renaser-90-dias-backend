package com.renaser.os.habits.api;

/**
 * Estado histórico de una obligación de hábito, tal como quedó registrado ese día.
 *
 * <p>Espeja {@code estado_registro} de la base. Se expone el enum completo y no un booleano
 * "cumplió/no cumplió" porque el seguimiento del mentor necesita distinguir cuatro cosas que
 * un booleano colapsa: lo que está por vencer, lo que venció sin hacerse, lo que se hizo y lo
 * que expiró. Un dato ausente no es incumplimiento (plan.md §7).
 */
public enum EstadoObligacion {

    PENDIENTE,
    EN_CURSO,
    COMPLETADO,
    FALLIDO,
    EXPIRADO;

    public boolean cumplido() {
        return this == COMPLETADO;
    }

    /** Ya no admite acción: el día se cerró sin cumplirse. */
    public boolean vencidoSinCumplir() {
        return this == FALLIDO || this == EXPIRADO;
    }
}
