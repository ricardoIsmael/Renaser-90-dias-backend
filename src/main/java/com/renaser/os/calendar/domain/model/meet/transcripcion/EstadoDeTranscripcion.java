package com.renaser.os.calendar.domain.model.meet.transcripcion;

/** En qué quedó la transcripción de una sesión por Meet (spec 047, FR-007). */
public enum EstadoDeTranscripcion {
    /** Todavía no está: se vuelve a buscar. */
    ESPERANDO,
    /** Guardada, cifrada. */
    LISTA,
    /** Pasaron 8 horas del fin y Google no tiene ninguna reunión en la sala. */
    SIN_REUNION,
    /** Hubo reunión pero no transcripción (la cuenta no dejó transcribir, o alguien la apagó). */
    SIN_TRANSCRIPCION,
    /** La cuenta conectada no tiene el permiso de leer las reuniones: hay que volver a conectarla. */
    SIN_PERMISO,
    /** Venció su plazo (o se borró la sala): ya no hay texto, queda la constancia. */
    BORRADA
}
