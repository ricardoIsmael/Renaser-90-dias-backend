package com.renaser.os.mentoring.domain.model.semaforo;

import com.renaser.os.points.api.ColorSemaforo;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cuántos aprendices hay en cada color del semáforo: el «5 al día, 2 requieren atención, 1 con
 * problemas» de la tabla del mentor y del resumen del líder (D-168,
 * docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §4.3 y §4.4).
 *
 * <p>El total se deriva de los cuatro colores y no se guarda aparte: así no hay forma de que los
 * contradiga.
 */
public record ConteoPorColor(int verde, int amarillo, int rojo, int sinDatos) {

    public static final ConteoPorColor NINGUNO = new ConteoPorColor(0, 0, 0, 0);

    public ConteoPorColor {
        if (verde < 0 || amarillo < 0 || rojo < 0 || sinDatos < 0) {
            throw new IllegalArgumentException("Un conteo por color no puede ser negativo");
        }
    }

    /** Un elemento por aprendiz: el color que tiene en la vista. */
    public static ConteoPorColor de(Collection<ColorSemaforo> colores) {
        Map<ColorSemaforo, Long> porColor = colores.stream().collect(Collectors.groupingBy(
                Function.identity(), () -> new EnumMap<>(ColorSemaforo.class), Collectors.counting()));
        return new ConteoPorColor(cuantos(porColor, ColorSemaforo.VERDE), cuantos(porColor, ColorSemaforo.AMARILLO),
                cuantos(porColor, ColorSemaforo.ROJO), cuantos(porColor, ColorSemaforo.SIN_DATOS));
    }

    public int total() {
        return verde + amarillo + rojo + sinDatos;
    }

    /** La suma de dos conteos: los totales del resumen del líder son la suma de sus grupos. */
    public ConteoPorColor mas(ConteoPorColor otro) {
        return new ConteoPorColor(verde + otro.verde, amarillo + otro.amarillo, rojo + otro.rojo,
                sinDatos + otro.sinDatos);
    }

    private static int cuantos(Map<ColorSemaforo, Long> porColor, ColorSemaforo color) {
        return porColor.getOrDefault(color, 0L).intValue();
    }
}
