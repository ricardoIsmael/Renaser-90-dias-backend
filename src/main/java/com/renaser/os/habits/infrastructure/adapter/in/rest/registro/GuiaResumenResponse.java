package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.GuiaResumen;

public record GuiaResumenResponse(String mantraTitulo, String mantraIntro, String queHacer, String comoHacerlo) {

    public static GuiaResumenResponse from(GuiaResumen g) {
        return new GuiaResumenResponse(g.mantraTitulo(), g.mantraIntro(), g.queHacer(), g.comoHacerlo());
    }
}
