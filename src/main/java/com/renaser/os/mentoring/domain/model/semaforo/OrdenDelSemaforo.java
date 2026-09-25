package com.renaser.os.mentoring.domain.model.semaforo;

import com.renaser.os.points.api.ColorSemaforo;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * El orden de las vistas de grupo del semáforo (docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §4.3):
 * rojo, amarillo, sin datos y verde; dentro de cada color, por nombre, y los que no tienen nombre
 * al final.
 *
 * <p>«Por nombre» es el orden alfabético castellano ({@link Collator} de {@code es}): comparando
 * {@code String} a secas, una «Ángela» quedaría después de «Zoila», porque la Á está después de la
 * Z en Unicode. Se crea un {@code Collator} por comparador y no uno compartido entre hilos.
 */
public final class OrdenDelSemaforo {

    private static final List<ColorSemaforo> PRIORIDAD =
            List.of(ColorSemaforo.ROJO, ColorSemaforo.AMARILLO, ColorSemaforo.SIN_DATOS, ColorSemaforo.VERDE);

    private OrdenDelSemaforo() {
    }

    /** El orden de una tabla: primero por color, después por nombre. */
    public static <T> Comparator<T> porColorYNombre(Function<T, ColorSemaforo> color, Function<T, String> nombre) {
        return Comparator.comparing(color, porPrioridad()).thenComparing(nombre, alfabetico());
    }

    /** Rojo, amarillo, sin datos, verde. */
    public static Comparator<ColorSemaforo> porPrioridad() {
        return Comparator.comparingInt(PRIORIDAD::indexOf);
    }

    /** Alfabético castellano; los null al final. */
    public static Comparator<String> alfabetico() {
        Collator castellano = Collator.getInstance(Locale.of("es"));
        return Comparator.nullsLast(castellano::compare);
    }
}
