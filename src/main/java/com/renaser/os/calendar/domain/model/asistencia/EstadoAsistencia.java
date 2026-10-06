package com.renaser.os.calendar.domain.model.asistencia;

/**
 * Cómo llegó una persona a una ocurrencia, al pasar lista (D-256, regla del dueño: «A tiempo / Tarde /
 * Ausente»). <b>Ausente no es un valor:</b> es no tener marca (sin fila en {@code asistencias_evento}).
 */
public enum EstadoAsistencia {

    A_TIEMPO,
    TARDE
}
