package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocamaestra;

import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Cuerpo de {@code PUT /api/v1/rocks/master/{eje}}.
 *
 * <p>Sin campo {@code participanteId} ni {@code eje}: el dueno sale de la sesion y el eje de la
 * ruta. Es el mismo blindaje de {@code CLAUDE.md} §5.3.3 — lo que el cliente no puede mandar, no
 * puede falsear.
 *
 * <p>{@code Digits} refleja {@code numeric(14,2)} de V35: sin eso, un numero mas largo llega hasta
 * la base y explota como error 500 en vez de como un 400 que explica que pasa.
 */
public record DefinirRocaMaestraRequest(
        @NotBlank @Size(max = RocaMaestra.MAX_OBJETIVO) String objetivo,
        @DecimalMin(value = "0.01") @Digits(integer = 12, fraction = 2) BigDecimal meta,
        @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal avance,
        @Size(max = 20) String unidad,
        /**
         * Desde donde arranca (V43, E-166). <b>Opcional</b>: sin el, el porcentaje usa la formula
         * vieja {@code avance / meta}, que asume que mas es mejor. Con el, mide el camino recorrido
         * y funciona igual para una meta que sube que para una que baja.
         *
         * <p>Se dejo opcional a proposito para no romper a un cliente que todavia no lo manda ni a
         * las filas anteriores a la migracion.
         */
        @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal lineaBase) {
}
