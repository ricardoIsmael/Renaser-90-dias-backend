package com.renaser.os.habits.infrastructure.adapter.in.rest.radar;

import com.renaser.os.habits.domain.model.radar.DiaDelRadar;
import com.renaser.os.habits.domain.model.radar.PlanDeMiRadar;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Mi Radar (spec 019): {@code encendido}, {@code hoy} (la fecha de hoy en la zona de la persona) y los {@code dias} de la
 * semana (lunes = 1 ... domingo = 7) ya resueltos con su Despertar y su Dormir. En el historial y en las fichas, {@code dias}
 * trae solo el de hoy.
 *
 * <p>Por dia: {@code modo}, {@code maximo} (las horas en punto que caben), {@code veces} (las que pregunta de verdad; 0 si
 * no hay Radar), {@code sinRadar}, {@code editable} (hoy y lo pasado no), {@code despertar}/{@code dormir} ("HH:mm" o null),
 * {@code horas} y {@code motivo} (SIN_DESPERTAR, SIN_DORMIR o HORARIO_INVALIDO si no hay horario).
 */
public record MiRadarResponse(boolean encendido, LocalDate hoy, List<DiaResponse> dias) {

    public static MiRadarResponse from(PlanDeMiRadar plan) {
        return new MiRadarResponse(plan.encendido(), plan.hoy(), plan.dias().stream().map(DiaResponse::from).toList());
    }

    /** Para las paginas del historial: {@code null} (sin el campo) mientras Mi Radar no aplica, en la semana 1. */
    public static MiRadarResponse deLaPagina(PlanDeMiRadar plan) {
        return plan == null ? null : from(plan);
    }

    public record DiaResponse(int dia, LocalDate fecha, String modo, int maximo, int veces, boolean sinRadar,
                              boolean editable, String despertar, String dormir, List<Integer> horas, String motivo) {

        static DiaResponse from(DiaDelRadar dia) {
            return new DiaResponse(dia.dia(), dia.fecha(), dia.modo().name(), dia.maximo(), dia.veces(), dia.sinRadar(),
                    dia.editable(), hora(dia.despertar()), hora(dia.dormir()), dia.horas(),
                    dia.motivo() == null ? null : dia.motivo().name());
        }

        private static String hora(LocalTime hora) {
            return hora == null ? null : String.format("%02d:%02d", hora.getHour(), hora.getMinute());
        }
    }
}
