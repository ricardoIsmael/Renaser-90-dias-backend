package com.renaser.os.points.infrastructure.adapter.out.persistence.ajuste;

public enum MotivoPuntosJpa {
    HABITO_COMPLETADO,
    HABITO_EXTENDIDO,
    HABITO_PERDIDO,
    HABITO_TARDE,
    BONO_RACHA,
    SANTUARIO_ROTO,
    EVIDENCIA_INVALIDA,
    EVIDENCIA_INVALIDA_REVERTIDA,
    SEMANA_SIN_CELULAR_PERDIDA,
    ROCA_COMPLETADA,
    ROCA_EXTENDIDA,
    /** Espejo del valor que agrega V60 al enum `motivo_puntos` de Postgres (D-146). */
    LECCION_COMPLETADA,
    AJUSTE_MANUAL
}
