package com.renaser.os.onboarding.infrastructure.adapter.in.rest.mapa;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * V06 completa, no una accion suelta: se guarda POR PASO. La lista puede venir vacia — el paso
 * puede quedar a medias y eso no es un error; el minimo se exige al activar.
 *
 * <p>El techo de {@code 6} tambien esta en el dominio ({@code AccionesDelMapa}): aca es validacion
 * de borde para devolver 400 en vez de 500, alla es el invariante.
 */
public record GuardarAccionesMapaRequest(@NotNull @Size(max = 6) List<@Valid Accion> actions) {

    public record Accion(@NotBlank String actionId,
                          @NotBlank String area,
                          @NotBlank @Size(min = 5, max = 100) String text,
                          @Min(1) @Max(7) int weeklyFrequency,
                          List<@Min(1) @Max(7) Integer> days,
                          String moment,
                          String evidence) {
    }
}
