package com.renaser.os.mentoring.api;

/**
 * Números simples, no un porcentaje de calificación: la nota del mentor es mensual y la calcula otro motor. Acá se
 * cuenta lo que pasó esta semana.
 */
public record ResumenSemana(int obligaciones, int cumplidas, int conEntrega, int pendientes, int sinCumplir) {
}
