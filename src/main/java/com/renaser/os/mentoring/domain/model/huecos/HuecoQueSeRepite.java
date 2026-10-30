package com.renaser.os.mentoring.domain.model.huecos;

import com.renaser.os.shared.domain.TramoDelDia;

import java.util.List;

/**
 * Un tramo del día que la persona tiene libre, durante al menos el mínimo útil, en varios días de la semana (FR-016).
 *
 * @param dias los días de la semana en que está libre, ISO-8601: 1 = lunes … 7 = domingo; siempre al menos
 *             {@link ReglasDeHuecos#DIAS_PARA_QUE_SE_REPITA}
 */
public record HuecoQueSeRepite(TramoDelDia tramo, List<Integer> dias) {

    public HuecoQueSeRepite {
        dias = List.copyOf(dias);
    }
}
