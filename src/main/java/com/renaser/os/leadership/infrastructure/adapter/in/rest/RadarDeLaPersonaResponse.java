package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.habits.api.CodigoRenaserDePersonaFinder.CodigoRenaserDePersona;
import com.renaser.os.habits.api.CodigoRenaserDePersonaFinder.MiRadarDeLaPersona;
import com.renaser.os.habits.api.CodigoRenaserDePersonaFinder.RegistroDelRadar;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * El Código Renaser de una persona, con la MISMA forma que {@code GET /api/v1/radar/history} (spec 012,
 * {@code contracts/api.md}) para que la app lea las tres fichas con un solo componente: {@code entries}, {@code
 * nextCursor} (null si no hay más) y {@code miRadar} (null en la semana 1, día menor que 8).
 */
public record RadarDeLaPersonaResponse(List<RegistroResponse> entries, Instant nextCursor, MiRadarResponse miRadar) {

    public static RadarDeLaPersonaResponse from(CodigoRenaserDePersona radar) {
        return new RadarDeLaPersonaResponse(radar.registros().stream().map(RegistroResponse::from).toList(),
                radar.siguienteCursor(), MiRadarResponse.from(radar.miRadar()));
    }

    /**
     * Mi Radar de la persona; el campo de la respuesta va en null en la semana 1, donde no aplica. {@code horas} son las
     * de todos los dias y {@code dias}, los dias de la semana con horas propias (lunes = 1 ... domingo = 7).
     */
    public record MiRadarResponse(boolean encendido, List<Integer> horas, List<DiaResponse> dias) {

        static MiRadarResponse from(MiRadarDeLaPersona miRadar) {
            return miRadar == null ? null : new MiRadarResponse(miRadar.encendido(), miRadar.horas(),
                    miRadar.dias().stream().map(dia -> new DiaResponse(dia.dia(), dia.horas())).toList());
        }
    }

    public record DiaResponse(int dia, List<Integer> horas) {
    }

    /** @param horaPrevista la hora local (0-23) a la que respondía; null si fue un registro libre */
    public record RegistroResponse(UUID id, String whatAmIDoing, String whatAmIThinking, String whatAmIFeeling,
                                   int energyLevel, String whatAmIAvoiding, Instant createdAt, Integer horaPrevista) {

        static RegistroResponse from(RegistroDelRadar r) {
            return new RegistroResponse(r.id(), r.queHago(), r.quePienso(), r.queSiento(), r.nivelEnergia(),
                    r.queEvito(), r.creadoEn(), r.horaPrevista());
        }
    }
}
