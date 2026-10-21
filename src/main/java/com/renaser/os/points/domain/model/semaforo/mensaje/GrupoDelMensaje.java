package com.renaser.os.points.domain.model.semaforo.mensaje;

/**
 * A qué grupo del catálogo pertenece el mensaje de un día (D-277, spec 006): el color del semáforo o, cuando el
 * día no tiene color, el caso en que está la persona. Son los ocho valores que repite el CHECK de
 * {@code mensajes_semaforo} (V110).
 */
public enum GrupoDelMensaje {

    VERDE,
    AMARILLO,
    ROJO,
    /** Antes del Día 1, o en los primeros días sin nada medido. */
    INICIO,
    /** Pasó la primera semana y no programó nada en la ventana vigente. */
    SIN_PROGRAMA,
    /** Pausó su semáforo (solo el personal con programa propio puede). */
    PAUSA,
    /** Volvió de tener la cuenta suspendida: la ventana tiene esos días y ninguno medido (D-209). */
    CUENTA_PAUSADA,
    /** Ya pasó el día 90. */
    GRADUADO;

    /** Si el grupo es uno de los tres colores del semáforo. */
    public boolean esDeColor() {
        return this == VERDE || this == AMARILLO || this == ROJO;
    }

    /** Si el grupo puede decir cuánto falta para el color siguiente: solo donde hay un color mejor al que subir. */
    public boolean admiteCuantoFalta() {
        return this == AMARILLO || this == ROJO;
    }
}
