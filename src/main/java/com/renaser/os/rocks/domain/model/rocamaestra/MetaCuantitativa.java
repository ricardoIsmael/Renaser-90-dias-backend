package com.renaser.os.rocks.domain.model.rocamaestra;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * La parte medible de una Roca Maestra: cuanto hay que llegar ({@code objetivo}), cuanto se
 * lleva ({@code avance}) y en que se mide ({@code unidad}).
 *
 * <p>Es un objeto de valor y no tres campos sueltos del agregado por una razon concreta: las
 * tres cosas solo tienen sentido juntas. Un avance sin meta no dibuja ninguna barra, y un
 * numero sin unidad no se puede ni escribir en pantalla. Teniendolas en un tipo propio, la
 * regla la impone el constructor una sola vez, y una Roca Maestra sin meta se representa con
 * esta referencia en {@code null} — un objetivo puramente cualitativo, que es valido.
 *
 * <p>{@code BigDecimal} y no {@code double}: hay plata de por medio (la meta tipica es
 * facturacion) y los binarios de coma flotante no representan exactamente valores decimales,
 * asi que sumar avances terminaria corrido por centavos.
 */
public record MetaCuantitativa(BigDecimal objetivo, BigDecimal avance, String unidad) {

    /** Tope de {@link #unidad}, alineado con {@code varchar(20)} de la tabla (V35). */
    public static final int MAX_UNIDAD = 20;

    private static final int PORCENTAJE_MAXIMO = 100;

    public MetaCuantitativa {
        Objects.requireNonNull(objetivo, "la meta es obligatoria");
        Objects.requireNonNull(avance, "el avance es obligatorio");
        if (objetivo.signum() <= 0) {
            throw new IllegalArgumentException("La meta tiene que ser mayor que cero");
        }
        if (avance.signum() < 0) {
            throw new IllegalArgumentException("El avance no puede ser negativo");
        }
        if (unidad == null || unidad.isBlank()) {
            throw new IllegalArgumentException("La unidad es obligatoria cuando hay una meta numerica");
        }
        unidad = unidad.trim();
        if (unidad.length() > MAX_UNIDAD) {
            throw new IllegalArgumentException("La unidad no puede pasar de " + MAX_UNIDAD + " caracteres");
        }
    }

    /** Meta recien definida, sin nada acumulado todavia. */
    public static MetaCuantitativa nueva(BigDecimal objetivo, String unidad) {
        return new MetaCuantitativa(objetivo, BigDecimal.ZERO, unidad);
    }

    /**
     * Porcentaje cumplido, entero, <b>acotado a 100</b>.
     *
     * <p>El tope es deliberado y no esconde nada: superar la meta es un exito y el valor real
     * sigue disponible en {@link #avance()}, pero quien consume esto es una barra de progreso,
     * y una barra al 140% se sale de su caja. Quien quiera mostrar el exceso tiene los dos
     * numeros crudos para hacerlo.
     */
    public int porcentaje() {
        int calculado = avance.multiply(BigDecimal.valueOf(PORCENTAJE_MAXIMO))
                .divide(objetivo, 0, RoundingMode.DOWN)
                .intValue();
        return Math.min(calculado, PORCENTAJE_MAXIMO);
    }

    /** Misma meta y unidad, otro acumulado. */
    public MetaCuantitativa conAvance(BigDecimal nuevoAvance) {
        return new MetaCuantitativa(objetivo, nuevoAvance, unidad);
    }
}
