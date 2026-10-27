package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocamaestra;

import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
        /**
         * Puede ser <b>0</b> desde V44: saldar una deuda es una meta legitima. Se admite acá y lo
         * valida el dominio, que es el que sabe la condicion —cero exige punto de partida— y puede
         * explicarla. Un {@code @DecimalMin} solo diria "debe ser mayor que 0.01", que es falso.
         */
        @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal meta,
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
        @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal lineaBase,
        /**
         * Quien escribe el objetivo (D-284). <b>Opcional</b>: ausente es "desde fuera del Mapa" (Plan, o un
         * APK anterior). La activacion del Mapa manda {@code "MAPA"}; asi el servidor distingue ese
         * {@code PUT}, que escribe las rocas ANTES de marcar el Mapa como activo, del de Plan. Cualquier otro
         * valor es un 400: mejor que se note un error de la app a que se ignore en silencio.
         */
        @Pattern(regexp = DefinirRocaMaestraRequest.ORIGEN_MAPA, message = "origen solo admite \"MAPA\"")
        String origen) {

    /** Unico valor que admite {@link #origen}. */
    public static final String ORIGEN_MAPA = "MAPA";

    /** {@code true} cuando el pedido lo manda la activacion del Mapa de Renacimiento. */
    public boolean desdeElMapa() {
        return ORIGEN_MAPA.equals(origen);
    }
}
