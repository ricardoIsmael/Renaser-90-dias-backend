package com.renaser.os.habits.domain.model.radar;

import java.time.LocalDate;
import java.util.List;

/**
 * Mi Radar de una persona con sus días resueltos (spec 019): lo que se lee en la pantalla, en Hoy y en las fichas.
 *
 * @param hoy  la fecha de hoy en la zona de la persona
 * @param dias los siete días de la semana que se muestra; en las fichas, solo el de hoy
 */
public record PlanDeMiRadar(boolean encendido, LocalDate hoy, List<DiaDelRadar> dias) {

    public PlanDeMiRadar {
        dias = List.copyOf(dias);
    }
}
