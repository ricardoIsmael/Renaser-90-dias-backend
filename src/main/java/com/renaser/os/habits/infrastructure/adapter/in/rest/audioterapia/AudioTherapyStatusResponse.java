package com.renaser.os.habits.infrastructure.adapter.in.rest.audioterapia;

import com.renaser.os.habits.application.ports.in.audioterapia.ConsultarAudioterapiaSemanalUseCase.AudioDeLaSemana;
import com.renaser.os.habits.application.ports.in.audioterapia.ConsultarAudioterapiaSemanalUseCase.EsperandoContenido;
import com.renaser.os.habits.application.ports.in.audioterapia.ConsultarAudioterapiaSemanalUseCase.EstadoAudioterapia;

public record AudioTherapyStatusResponse(Integer semana, String titulo, String url, Integer diaSiguienteCambio) {

    public static AudioTherapyStatusResponse from(EstadoAudioterapia estado) {
        return switch (estado) {
            case AudioDeLaSemana audio -> new AudioTherapyStatusResponse(audio.semanaActual(), audio.titulo(),
                    audio.url(), audio.diaSiguienteCambio());
            case EsperandoContenido ignored -> new AudioTherapyStatusResponse(null, null, null, null);
        };
    }
}
