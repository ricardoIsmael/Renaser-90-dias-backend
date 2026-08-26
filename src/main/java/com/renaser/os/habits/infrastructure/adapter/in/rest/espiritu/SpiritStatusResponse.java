package com.renaser.os.habits.infrastructure.adapter.in.rest.espiritu;

import com.renaser.os.habits.application.ports.in.espiritu.ConsultarEstadoEspirituUseCase.EstadoEspiritu;

import java.util.List;

public record SpiritStatusResponse(List<SpiritDayResponse> days, Integer currentDay) {

    public static SpiritStatusResponse from(EstadoEspiritu estado) {
        return new SpiritStatusResponse(estado.dias().stream().map(SpiritDayResponse::from).toList(),
                estado.diaActual());
    }
}
