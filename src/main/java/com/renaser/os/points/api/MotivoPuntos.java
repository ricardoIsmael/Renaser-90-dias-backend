package com.renaser.os.points.api;

/**
 * Motivo de un ajuste de puntos. Vive en {@code points.api} (no en
 * {@code points.domain.model.ajuste}, donde estaba originalmente) porque es un
 * parámetro de {@link AjustarPuntosPort#ajustar}, la única puerta de entrada
 * pública de {@code points} para otros módulos (primer consumidor real: {@code rocks},
 * luego {@code habits}). Dejarlo en un paquete interno de {@code points} obligaría a
 * quien lo llame a importar un tipo fuera de {@code @NamedInterface("api")} — la
 * misma fuga de tipos internos documentada en {@code docs/MODULO_PHASECONTRACTS.md}
 * §2.1 y {@code docs/MODULO_SUPPORT.md} §3 para {@code users.api.UserSummary}/{@code UserRole}.
 *
 * <p>Movido 2026-08-24 (ver {@code docs/MODULO_ROCKS.md}, decisión RK-1). Un solo
 * enum, sin copia paralela en {@code domain}: todo el módulo {@code points} lo usa
 * desde acá (dominio, aplicación y persistencia), evitando el riesgo de que las dos
 * copias diverjan.
 */
public enum MotivoPuntos {

    HABIT_COMPLETED,
    /** Hábito completado dentro de la extensión configurable (3 puntos fijos). */
    HABIT_EXTENDED,
    /** Hábito obligatorio que expiró sin completarse. */
    MISSED_HABIT,
    /** Hábito completado pasada su hora límite (uso legado; hoy HABIT_COMPLETED/EXTENDED cubren la escala). */
    LATE_HABIT,
    /** Bono por racha de días al 100% de hábitos (cada 3er día, +5 puntos). */
    STREAK_BONUS,
    /** Sesión de Santuario (phone-free) interrumpida. */
    SANCTUARY_BREAK,
    /** Evidencia rechazada por la IA. */
    INVALID_EVIDENCE,
    /** Devolución de INVALID_EVIDENCE cuando la revisión humana revoca el rechazo. */
    INVALID_EVIDENCE_REVOKED,
    /** Semana cerrada sin ningún ciclo de 24h de Santuario completado. */
    PHONE_FREE_WEEK_MISSED,
    /** Roca diaria completada dentro de su ventana o gracia (misma escala que hábitos). */
    ROCK_COMPLETED,
    /** Roca diaria completada dentro de la extensión (puntos fijos). */
    ROCK_EXTENDED,
    /** Corrección administrativa manual (resets, ajustes de soporte). */
    MANUAL_ADJUSTMENT
}
