package com.renaser.os.mentoring.domain.model.huecos;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Un hábito que ese día ocupa tiempo: le toca y tiene hora de inicio. Un hábito sin hora fija, apagado, pausado o que
 * todavía no empieza no llega acá (FR-003 y FR-004): lo decide quien arma el día.
 *
 * @param claveSistema {@code WAKE_UP} o {@code SLEEP} reconocen a Despertar y Dormir; cualquier otra, o {@code null},
 *                     es un hábito más
 */
public record HabitoConHora(String claveSistema, LocalTime horaDisparo) {

    public static final String DESPERTAR = "WAKE_UP";
    public static final String DORMIR = "SLEEP";

    public ItemPlanificado enElDia(LocalDate dia) {
        return ItemPlanificado.deHabito(dia, horaDisparo);
    }
}
