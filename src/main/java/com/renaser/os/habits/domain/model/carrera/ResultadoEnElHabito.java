package com.renaser.os.habits.domain.model.carrera;

/**
 * Qué le pasó al hábito KILÓMETROS DIARIOS con una carrera (contrato de la spec 042, {@code habito.resultado}). La carrera se guarda
 * en todos los casos; esto solo dice si entró al hábito y cómo.
 */
public enum ResultadoEnElHabito {

    /** El registro del día estaba pendiente y esta carrera lo cerró: se pagaron los puntos ahora. */
    CERRADO,
    /** Ya estaba cumplido por GPS o Health Connect y los km del día subieron (FR-022); sin puntos. */
    ACTUALIZADO,
    /** Ya estaba cumplido por una fuente automática con más km: nada cambió. */
    SIN_CAMBIO,
    /** Cumplido con la captura (MANUAL): no se toca. */
    YA_CON_CAPTURA,
    /** El día de la carrera ya terminó para la persona (D-259): no se aplica a ningún día. */
    DIA_CERRADO,
    /** La persona no tiene el hábito de km ese día (formación, pausa, Día 0, sin programa). */
    SIN_HABITO,
    /** La carrera no cuenta (FR-024: velocidad, más de 60 km, hora del teléfono). */
    NO_CUENTA
}
