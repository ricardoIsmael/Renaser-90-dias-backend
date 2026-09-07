package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocamensual;

import com.renaser.os.rocks.domain.model.rocamensual.RocaMensual;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Cuerpo de {@code PUT /api/v1/rocks/monthly/{eje}/{numeroMes}}.
 *
 * <p>Sin campo {@code participanteId}, ni {@code eje}, ni {@code numeroMes}: el dueno sale de la
 * sesion y los otros dos de la ruta. Es el mismo blindaje de {@code CLAUDE.md} §5.3.3 — lo que el
 * cliente no puede mandar, no puede falsear.
 *
 * <p>{@code Digits} refleja {@code numeric(14,2)} de V36: sin eso, un numero mas largo llega hasta
 * la base y explota como error 500 en vez de como un 400 que explica que pasa.
 */
public record DefinirRocaMensualRequest(
        @NotBlank @Size(max = RocaMensual.MAX_TITULO) String titulo,
        @DecimalMin(value = "0.01") @Digits(integer = 12, fraction = 2) BigDecimal meta,
        @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal avance,
        @Size(max = 20) String unidad) {
}
