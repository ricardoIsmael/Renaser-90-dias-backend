package com.renaser.os.habits.infrastructure.adapter.in.rest.santuario;

import com.renaser.os.habits.domain.model.santuario.SesionBloqueo;

import java.time.Instant;

public record SesionBloqueoResponse(String registroHabitoId, String estado, Instant iniciadaEn, Instant terminadaEn,
                                     int duracionMinimaMin, String motivoSalida) {

    public static SesionBloqueoResponse from(SesionBloqueo s) {
        return new SesionBloqueoResponse(s.registroHabitoId().toString(), s.estado().name(), s.iniciadaEn(),
                s.terminadaEn(), s.duracionMinimaMin(), s.motivoSalida() != null ? s.motivoSalida().name() : null);
    }
}
