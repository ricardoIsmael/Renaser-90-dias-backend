package com.renaser.os.habits.infrastructure.adapter.in.rest.radar;

import com.renaser.os.habits.application.ports.in.radar.ConsultarHistorialRadarUseCase.HistorialRadarPage;

import java.time.Instant;
import java.util.List;

/**
 * Espejo de `RadarHistoryPage` (radar.ts:345-350): `entries` + `nextCursor` (null si no hay mas).
 * {@code miRadar} (spec 012): como tiene Mi Radar la persona de este historial; {@code null} en la semana 1 (dia
 * menor que 8), donde no aplica. Aditivo, el APK publicado lo ignora.
 */
public record RadarHistoryPageResponse(List<RegistroRadarResponse> entries, Instant nextCursor,
                                       MiRadarResponse miRadar) {

    public static RadarHistoryPageResponse from(HistorialRadarPage page) {
        return new RadarHistoryPageResponse(page.entradas().stream().map(RegistroRadarResponse::from).toList(),
                page.siguienteCursor(), MiRadarResponse.deLaPagina(page.miRadar()));
    }
}
