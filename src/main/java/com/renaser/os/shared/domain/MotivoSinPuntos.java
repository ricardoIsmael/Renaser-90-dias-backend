package com.renaser.os.shared.domain;

/**
 * Spec 048 (D14, FR-015): por qué algo hecho sin red que daba puntos queda registrado SIN ellos. Nada rebota: se registra igual y la
 * respuesta lo dice con este nombre para que la app lo muestre. Son los mismos cuatro de los hábitos de la fase 1.
 */
public enum MotivoSinPuntos {
    /** Llegó más de 48 h después de hecho. */
    LLEGO_DESPUES_DE_48_H,
    /** La hora dice que se hizo en el futuro (más de 2 minutos). */
    HORA_FUTURA,
    /** Lo que se hizo era de un día que no es el de la hora real. */
    FUERA_DE_SU_DIA,
    /** Ya estaba hecho (con red o desde otro teléfono): no se toca. */
    YA_ESTABA_HECHO
}
