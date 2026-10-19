package com.renaser.os.points.domain.model.semaforo.mensaje;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.domain.model.semaforo.ReglaDelSemaforo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cuántos pendientes de hoy le faltan a la persona para subir al color siguiente (FR-006, {@code {faltan}}).
 * Cuenta lo mismo que el semáforo —hábitos + objetivos del día, D-168— y no inventa umbrales: prueba cada
 * resultado posible de hoy con {@link ReglaDelSemaforo}, la misma aritmética del semáforo (porcentaje del día a
 * entero, promedio a un decimal, cortes 80 y 60).
 *
 * <p><b>Qué ventana mira.</b> La tarjeta muestra los siete días cerrados que terminan ayer; mañana la ventana
 * serán los seis últimos de esos siete más hoy. Lo que se cumpla hoy es lo que mueve el color de mañana, así que
 * se prueba cada cumplimiento posible de hoy sobre esa ventana de mañana.
 *
 * <p>Solo hay respuesta si falta algo por hacer, hay un color mejor que alcanzar con lo que queda del día y hoy es
 * un día medido; en cualquier otro caso (ya alcanzó el color siguiente, ni cumpliendo todo sube, hoy no cuenta,
 * ya está en verde) el dato no existe y se eligen mensajes que no lo pidan.
 */
public final class HabitosQueFaltan {

    private static final int DIAS_QUE_SE_CONSERVAN = 6;

    private HabitosQueFaltan() {
    }

    /**
     * @param colorVigente el color de la ventana que muestra la tarjeta
     * @param ventana      los 7 días de esa ventana, del más viejo al más nuevo
     * @param hoy          el día en curso de la persona, con la misma cuenta del semáforo en vivo
     *                     ({@code CumplimientoDelDia.aDia()}), armado con lo que {@code /home} ya leyó
     */
    public static Optional<Integer> de(ColorSemaforo colorVigente, List<DiaDelSemaforo> ventana, DiaDelSemaforo hoy) {
        if (!mejorable(colorVigente) || hoy.estado() != EstadoDiaSemaforo.MEDIDO) {
            return Optional.empty();
        }
        int programados = hoy.habitosProgramados() + hoy.objetivosProgramados();
        int cumplidos = hoy.habitosCumplidos() + hoy.objetivosCumplidos();
        List<Integer> previos = porcentajesDeLosUltimosSeis(ventana);
        if (sube(colorVigente, previos, cumplidos, programados)) {
            return Optional.empty();
        }
        for (int posible = cumplidos + 1; posible <= programados; posible++) {
            if (sube(colorVigente, previos, posible, programados)) {
                return Optional.of(posible - cumplidos);
            }
        }
        return Optional.empty();
    }

    private static boolean mejorable(ColorSemaforo color) {
        return color == ColorSemaforo.AMARILLO || color == ColorSemaforo.ROJO;
    }

    /** Si con {@code cumplidos} de {@code programados} hoy, la ventana de mañana queda en un color mejor. */
    private static boolean sube(ColorSemaforo colorVigente, List<Integer> previos, int cumplidos, int programados) {
        List<Integer> ventanaDeManana = new ArrayList<>(previos);
        ventanaDeManana.add(ReglaDelSemaforo.porcentajeDelDia(cumplidos, programados));
        ColorSemaforo nuevo = ReglaDelSemaforo.colorDe(ReglaDelSemaforo.promedio(ventanaDeManana).orElse(null));
        return nivel(nuevo) > nivel(colorVigente);
    }

    /** De peor a mejor. Se dice acá y no con el orden del enum, que no promete nada sobre qué color es mejor. */
    private static int nivel(ColorSemaforo color) {
        return switch (color) {
            case ROJO -> 0;
            case AMARILLO -> 1;
            case VERDE -> 2;
            case SIN_DATOS -> -1;
        };
    }

    private static List<Integer> porcentajesDeLosUltimosSeis(List<DiaDelSemaforo> ventana) {
        int desde = Math.max(0, ventana.size() - DIAS_QUE_SE_CONSERVAN);
        return ventana.subList(desde, ventana.size()).stream()
                .filter(dia -> dia.estado() == EstadoDiaSemaforo.MEDIDO)
                .map(DiaDelSemaforo::porcentaje)
                .toList();
    }
}
