package com.renaser.os.habits.infrastructure.adapter.in.rest.radar;

import com.renaser.os.habits.application.ports.in.radar.ConsultarHistorialRadarUseCase.HistorialRadarPage;

import java.time.Instant;
import java.util.List;

/** Espejo de `RadarHistoryPage` (radar.ts:345-350): `entries` + `nextCursor` (null si no hay mas). */
public record RadarHistoryPageResponse(List<RegistroRadarResponse> entries, Instant nextCursor) {

    public static RadarHistoryPageResponse from(HistorialRadarPage page) {
        return new RadarHistoryPageResponse(page.entradas().stream().map(RegistroRadarResponse::from).toList(),
                page.siguienteCursor());
    }
}
