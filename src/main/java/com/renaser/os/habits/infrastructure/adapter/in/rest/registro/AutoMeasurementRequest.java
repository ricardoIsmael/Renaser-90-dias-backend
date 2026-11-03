package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import com.renaser.os.habits.domain.model.medicion.DispositivoDeMedicion;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code POST /api/v1/habit-tracks/{id}/auto-measurement} (spec 037): lo que leyó el teléfono de Health Connect. Sin campo
 * {@code puntos} ni {@code origen}: el otorgamiento lo calcula el servidor y el origen de este endpoint es siempre Health
 * Connect (CLAUDE.MD §5.3.3). Aquí solo se exige la forma; si los km llegan a la meta, y los topes, los decide
 * {@code PoliticaKilometros} (un 400 que dice qué falta). {@code kilometros} es un número JSON con punto decimal.
 */
public record AutoMeasurementRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("99999.99") BigDecimal kilometros,
        @PositiveOrZero Integer pasos,
        @NotBlank @Size(max = 60) String fuente,
        @NotNull DispositivoDeMedicion dispositivo,
        @NotNull Instant leidoEn) {
}
