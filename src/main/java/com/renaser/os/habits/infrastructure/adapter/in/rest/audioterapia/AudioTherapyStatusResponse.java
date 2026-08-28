package com.renaser.os.habits.infrastructure.adapter.in.rest.audioterapia;

import com.renaser.os.habits.application.ports.in.audioterapia.ConsultarAudioterapiaSemanalUseCase.EstadoAudioterapia;

public record AudioTherapyStatusResponse(Integer semana, String titulo, String url, Integer diaSiguienteCambio) {

    public static AudioTherapyStatusResponse from(EstadoAudioterapia estado) {
        return estado.audio()
                .map(audio -> new AudioTherapyStatusResponse(estado.semanaActual().orElse(null), audio.titulo(),
                        audio.url(), audio.diaSiguienteCambio()))
                .orElseGet(() -> new AudioTherapyStatusResponse(null, null, null, null));
    }
}
