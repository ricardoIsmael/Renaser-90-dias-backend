package com.renaser.os.points.api;

import java.time.LocalDate;

/**
 * Pausa del semáforo de alguien del staff con programa propio: de {@code desde} a {@code hasta},
 * ambos inclusive, fechas locales. Esos días no se miden. El aprendiz nunca tiene pausa.
 */
public record PausaDelSemaforo(LocalDate desde, LocalDate hasta) {
}
