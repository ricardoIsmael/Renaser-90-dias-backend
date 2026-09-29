package com.renaser.os.habits.domain.model.medicion;

/**
 * En qué se mide un hábito que registra un número por día (D-226).
 *
 * <p>No se guarda en la base: la declara la política del hábito ({@code PoliticaHabito#unidadDeMedicion}),
 * que es donde ya viven las reglas propias de cada hábito del catálogo. Hoy un solo valor; los pasos
 * (Health Connect / HealthKit) serían el segundo, con su propia política.
 */
public enum UnidadMedicion {

    /** Kilómetros recorridos en el día (hábito {@code DAILY_KM}). */
    KILOMETROS
}
