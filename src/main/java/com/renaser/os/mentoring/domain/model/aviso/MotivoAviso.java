package com.renaser.os.mentoring.domain.model.aviso;

/** Por qué se le avisa al mentor. Cada motivo se explica con su causa y sus fechas. */
public enum MotivoAviso {

    /** Días locales completos sin registrar nada. Umbral configurable, 3 por defecto (P-06). */
    SIN_ACTIVIDAD,

    /** Obligaciones que exigían evidencia, ya vencidas y sin entrega. Nunca antes de vencer. */
    EVIDENCIA_VENCIDA
}
