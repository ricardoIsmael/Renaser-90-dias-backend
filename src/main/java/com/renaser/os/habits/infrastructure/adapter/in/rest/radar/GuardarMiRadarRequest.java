package com.renaser.os.habits.infrastructure.adapter.in.rest.radar;

import com.renaser.os.habits.domain.model.radar.MiRadar.DiaPropio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * {@code PUT /api/v1/radar/mi-radar} (spec 012). {@code horas} son las de todos los dias; {@code dias}, los dias de la
 * semana (lunes = 1 ... domingo = 7) con horas propias, y uno con {@code horas: []} es «Sin Radar ese dia». Sin
 * {@code dias} todos los dias siguen {@code horas}. Con {@code encendido: false} las horas y los dias se ignoran. Las
 * reglas de las horas (de 1 a 12, en punto, de 3 a 23, sin repetir) y de los dias (1 a 7, sin repetir) las valida el
 * dominio ({@code MiRadar}): un 400 con su mensaje.
 */
public record GuardarMiRadarRequest(@NotNull Boolean encendido, List<Integer> horas,
                                    List<@NotNull @Valid DiaDeMiRadarRequest> dias) {

    /**
     * Un dia con horas propias. Sin {@code horas} es un 400: «Sin Radar ese dia» se dice con {@code []}, no con la
     * ausencia.
     */
    public record DiaDeMiRadarRequest(@NotNull Integer dia, @NotNull List<Integer> horas) {
    }

    /** Los dias como los entiende el caso de uso; {@code null} si el cuerpo no trae {@code dias}. */
    public List<DiaPropio> diasPropios() {
        return dias == null ? null : dias.stream().map(propio -> new DiaPropio(propio.dia(), propio.horas())).toList();
    }
}
