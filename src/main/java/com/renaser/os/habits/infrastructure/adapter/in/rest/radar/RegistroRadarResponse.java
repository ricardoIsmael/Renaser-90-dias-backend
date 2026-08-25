package com.renaser.os.habits.infrastructure.adapter.in.rest.radar;

import com.renaser.os.habits.domain.model.radar.RegistroRadar;

import java.time.Instant;

/** Proyeccion explicita de salida (CLAUDE.MD §5.4.1) — nunca la entidad de dominio serializada. */
public record RegistroRadarResponse(String id, String whatAmIDoing, String whatAmIThinking, String whatAmIFeeling,
                                     int energyLevel, String whatAmIAvoiding, Instant createdAt) {

    public static RegistroRadarResponse from(RegistroRadar r) {
        return new RegistroRadarResponse(r.id().toString(), r.queHago(), r.quePienso(), r.queSiento(),
                r.nivelEnergia(), r.queEvito(), r.creadoEn());
    }
}
