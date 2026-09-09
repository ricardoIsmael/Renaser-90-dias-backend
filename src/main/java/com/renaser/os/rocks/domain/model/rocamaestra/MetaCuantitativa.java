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
public record MetaCuantitativa(BigDecimal objetivo, BigDecimal avance, String unidad, BigDecimal lineaBase) {

    /** Tope de {@link #unidad}, alineado con {@code varchar(20)} de la tabla (V35). */
    public static final int MAX_UNIDAD = 20;

    private static final int PORCENTAJE_MAXIMO = 100;

    public MetaCuantitativa {
        Objects.requireNonNull(objetivo, "la meta es obligatoria");
        Objects.requireNonNull(avance, "el avance es obligatorio");
        if (objetivo.signum() < 0) {
            throw new IllegalArgumentException("La meta no puede ser negativa");
        }
        if (objetivo.signum() == 0 && lineaBase == null) {
            // Llegar a cero es una meta legitima -saldar una deuda, dejar de fumar- pero solo se
            // puede medir sabiendo desde donde se arranco: sin punto de partida el porcentaje seria
            // `avance / 0`. Con linea base, `|avance - base| / |0 - base|` funciona perfecto.
            throw new IllegalArgumentException(
                    "Una meta de cero necesita punto de partida: sin el no hay avance que medir");
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
        if (lineaBase != null) {
            if (lineaBase.signum() < 0) {
                throw new IllegalArgumentException("La linea base no puede ser negativa");
            }
            if (lineaBase.compareTo(objetivo) == 0) {
                // Sin distancia entre el punto de partida y la meta no hay avance que medir, y el
                // porcentaje seria una division por cero. El Mapa ya lo bloquea al escribirlo.
                throw new IllegalArgumentException(
                        "La meta tiene que ser distinta del punto de partida: no habria avance que medir");
            }
        }
    }

    /** Meta recien definida, sin nada acumulado todavia. */
    /** Meta ascendente clasica: se arranca en cero y se sube. No admite objetivo cero. */
    public static MetaCuantitativa nueva(BigDecimal objetivo, String unidad) {
        return new MetaCuantitativa(objetivo, BigDecimal.ZERO, unidad, null);
    }

    /** Meta con punto de partida declarado: es la unica forma de medir una meta que baja. */
    public static MetaCuantitativa desde(BigDecimal lineaBase, BigDecimal objetivo, String unidad) {
        return new MetaCuantitativa(objetivo, lineaBase, unidad, lineaBase);
    }

    /** {@code true} cuando la meta es menor que el punto de partida: bajar de peso, bajar deuda. */
    public boolean esDescendente() {
        return lineaBase != null && objetivo.compareTo(lineaBase) < 0;
    }

    /**
     * Porcentaje cumplido, entero, <b>acotado a 100</b>.
     *
     * <p>El tope es deliberado y no esconde nada: superar la meta es un exito y el valor real
     * sigue disponible en {@link #avance()}, pero quien consume esto es una barra de progreso,
     * y una barra al 140% se sale de su caja. Quien quiera mostrar el exceso tiene los dos
     * numeros crudos para hacerlo.
     */
    /**
     * Cuanto se avanzo, de 0 a 100.
     *
     * <p><b>Con punto de partida</b> se mide el camino recorrido entre donde arranco y donde
     * quiere llegar, y por eso funciona en las dos direcciones:
     * {@code |avance - lineaBase| / |objetivo - lineaBase|}. Alguien que va de 82 kg a 75 esta al
     * 0 % el primer dia y al 100 % cuando llega, igual que alguien que va de 5000 a 15 000.
     *
     * <p><b>Sin punto de partida</b> se conserva la formula vieja, {@code avance / objetivo}, que
     * asume que se arranca en cero y que mas es mejor. Es lo unico que se puede hacer con las filas
     * anteriores a la columna {@code linea_base} (V43), que no lo tienen.
     *
     * > <b>Corregido 2026-09-09 (E-166).</b> Antes existia solo la segunda formula. Con
     * > "pesare 75 kg partiendo de 82" daba 82 x 100 / 75 = 109, acotado a 100: el aprendiz veia
     * > <b>100 % CUMPLIDO el primer dia</b>, al lado de "Llevas 82 kg, Meta 75 kg". Un porcentaje
     * > calculado sobre dos numeros sin saber hacia donde mejora el indicador es una suposicion,
     * > no un calculo.
     */
    public int porcentaje() {
        if (lineaBase == null) {
            return acotar(avance.multiply(BigDecimal.valueOf(PORCENTAJE_MAXIMO))
                    .divide(objetivo, 0, RoundingMode.DOWN)
                    .intValue());
        }
        BigDecimal recorrido = avance.subtract(lineaBase).abs();
        BigDecimal total = objetivo.subtract(lineaBase).abs();
        // Retroceder por debajo del punto de partida no es "avance negativo": es 0 % recorrido.
        if (seAlejo()) {
            return 0;
        }
        return acotar(recorrido.multiply(BigDecimal.valueOf(PORCENTAJE_MAXIMO))
                .divide(total, 0, RoundingMode.DOWN)
                .intValue());
    }

    /** El avance quedo del lado contrario al que empuja la meta. */
    private boolean seAlejo() {
        return esDescendente() ? avance.compareTo(lineaBase) > 0 : avance.compareTo(lineaBase) < 0;
    }

    private static int acotar(int porcentaje) {
        return Math.min(Math.max(porcentaje, 0), PORCENTAJE_MAXIMO);
    }

    /** Misma meta y unidad, otro acumulado. */
    public MetaCuantitativa conAvance(BigDecimal nuevoAvance) {
        return new MetaCuantitativa(objetivo, nuevoAvance, unidad, lineaBase);
    }
}
