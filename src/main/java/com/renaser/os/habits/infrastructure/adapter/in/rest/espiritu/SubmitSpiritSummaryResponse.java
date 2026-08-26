package com.renaser.os.habits.infrastructure.adapter.in.rest.espiritu;

import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituUseCase.ResultadoEntrega;

public record SubmitSpiritSummaryResponse(boolean onTime) {

    public static SubmitSpiritSummaryResponse from(ResultadoEntrega resultado) {
        return new SubmitSpiritSummaryResponse(resultado.aTiempo());
    }
}
