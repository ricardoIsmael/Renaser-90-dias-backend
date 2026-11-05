package com.renaser.os.calendar.domain.model.meet;

/**
 * En qué quedó la sala de Meet de UNA fecha de un evento (spec 040, FR-012).
 *
 * <ul>
 *   <li>{@link #CREADO}: Google tiene la sala y la fila tiene su enlace.</li>
 *   <li>{@link #PENDIENTE}: Google todavía no terminó de crearla (respondió {@code pending}) o aún no se la pidió.</li>
 *   <li>{@link #FALLIDO}: no se pudo crear; el motivo queda en la fila y el evento existe igual (SC-003).</li>
 *   <li>{@link #PENDIENTE_DE_SINCRONIZAR}: Renaser cambió (editó o borró) y Google todavía no se enteró.</li>
 *   <li>{@link #BORRADO}: el evento de Google ya se borró. La fila queda como lápida: Google no reutiliza un id borrado.</li>
 * </ul>
 */
public enum EstadoSalaMeet {

    CREADO,
    PENDIENTE,
    FALLIDO,
    PENDIENTE_DE_SINCRONIZAR,
    BORRADO;

    /** Si falta que Renaser le diga algo a Google (crear, actualizar o borrar). */
    public boolean faltaTrabajo() {
        return this == PENDIENTE || this == FALLIDO || this == PENDIENTE_DE_SINCRONIZAR;
    }
}
