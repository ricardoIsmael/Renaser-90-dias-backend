package com.renaser.os.habits.infrastructure.adapter.in.rest.carrera;

import com.renaser.os.habits.domain.model.carrera.DatosDeCarrera;
import com.renaser.os.habits.domain.model.carrera.RutaDeCarrera;
import com.renaser.os.habits.domain.model.carrera.Tramo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code POST /api/v1/carreras} (spec 042, contrato §1). Sin participante, puntos, día ni resultado: la persona es quien está
 * autenticada y lo demás lo calcula el servidor (CLAUDE.MD §5.3.3). Aquí solo se exige la forma; los topes los garantiza
 * {@link DatosDeCarrera} (un 400 con un texto que la persona lee) y si cuenta para el hábito lo decide {@code PoliticaKilometros}.
 */
public record CarreraRequest(@NotNull UUID carreraId, @NotNull Instant iniciadaEn, @NotNull Instant terminadaEn,
                             @NotNull Integer duracionActivaSeg, @NotNull BigDecimal distanciaMetros, Integer subidaMetros,
                             Integer pasos, Integer caloriasAprox, @NotNull @Size(max = 61) List<@Valid @NotNull TramoRequest> tramos,
                             Integer metrosSinGps, @Valid RutaRequest ruta, String appVersion) {

    public record TramoRequest(@NotNull Integer km, @NotNull Integer segundos) {
    }

    public record RutaRequest(@NotNull String polyline, @NotNull Integer puntos) {
    }

    DatosDeCarrera aDatos() {
        return new DatosDeCarrera(carreraId, iniciadaEn, terminadaEn, duracionActivaSeg, distanciaMetros, subidaMetros, pasos,
                caloriasAprox, tramos.stream().map(t -> new Tramo(t.km(), t.segundos())).toList(), metrosSinGps,
                ruta == null ? null : new RutaDeCarrera(ruta.polyline(), ruta.puntos()), appVersion);
    }
}
