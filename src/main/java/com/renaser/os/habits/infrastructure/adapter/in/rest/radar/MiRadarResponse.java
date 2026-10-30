package com.renaser.os.habits.infrastructure.adapter.in.rest.radar;

import com.renaser.os.habits.domain.model.radar.MiRadar;

import java.util.List;

/**
 * Mi Radar (spec 012): {@code encendido}; {@code horas}, las de todos los dias, en punto y de menor a mayor; y
 * {@code dias}, los dias de la semana con horas propias (lunes = 1 ... domingo = 7), cada uno con sus {@code horas}
 * (vacias = «Sin Radar ese dia»). Apagado no tiene horas ni dias; un dia que no esta en {@code dias} sigue las horas de
 * todos los dias.
 */
public record MiRadarResponse(boolean encendido, List<Integer> horas, List<DiaResponse> dias) {

    public static MiRadarResponse from(MiRadar miRadar) {
        return new MiRadarResponse(miRadar.estaEncendido(), miRadar.horas(),
                miRadar.dias().stream().map(propio -> new DiaResponse(propio.dia(), propio.horas())).toList());
    }

    /** Para las paginas del historial: {@code null} (sin el campo) mientras Mi Radar no aplica, en la semana 1. */
    public static MiRadarResponse deLaPagina(MiRadar miRadar) {
        return miRadar == null ? null : from(miRadar);
    }

    public record DiaResponse(int dia, List<Integer> horas) {
    }
}
