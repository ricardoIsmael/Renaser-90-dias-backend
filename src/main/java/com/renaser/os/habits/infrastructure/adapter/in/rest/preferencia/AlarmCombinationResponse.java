package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import com.renaser.os.habits.domain.model.preferencia.CombinacionDeAlarma;

/**
 * Como suena la alarma, YA RESUELTA, en las lecturas de {@code habit-preferences} (spec 009, D-283): en un
 * habito es la general ({@code alarm}) y en un dia de la semana es la propia o, si no tiene, la general. Los
 * cuatro campos siempre vienen: lo que nadie eligio es la combinacion de hoy.
 */
public record AlarmCombinationResponse(boolean sound, boolean vibration, boolean voice, boolean repeat) {

    static AlarmCombinationResponse from(CombinacionDeAlarma combinacion) {
        return new AlarmCombinationResponse(combinacion.sonido(), combinacion.vibracion(), combinacion.voz(),
                combinacion.repetir());
    }
}
