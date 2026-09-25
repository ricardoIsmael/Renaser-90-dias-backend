package com.renaser.os.mentoring.application.services;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.api.SemanaDelSemaforo;
import com.renaser.os.points.api.VentanaDelSemaforo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Ventanas del semáforo coherentes para las pruebas (regla 03: un fixture no se contradice). El
 * promedio, el color y los días con datos se derivan de los días, igual que en {@code points}.
 */
public final class VentanasDePrueba {

    /** Hábitos de cada día medido: con 20, el porcentaje de un día va de 5 en 5. */
    private static final int HABITOS_POR_DIA = 20;

    private VentanasDePrueba() {
    }

    /**
     * Siete días desde {@code desde}, con el porcentaje de cada día (null = sin nada programado).
     * Cada día medido tuvo 20 hábitos y cumplió {@code porcentaje / 5}.
     */
    public static VentanaDelSemaforo ventana(LocalDate desde, boolean cerrada, Integer... porcentajes) {
        if (porcentajes.length != SemanaDelSemaforo.DIAS) {
            throw new IllegalArgumentException("Una ventana tiene 7 dias, no " + porcentajes.length);
        }
        List<DiaDelSemaforo> dias = new ArrayList<>();
        for (int i = 0; i < porcentajes.length; i++) {
            dias.add(dia(desde.plusDays(i), porcentajes[i]));
        }
        List<Integer> medidos = Stream.of(porcentajes).filter(Objects::nonNull).toList();
        BigDecimal promedio = medidos.isEmpty() ? null
                : BigDecimal.valueOf(medidos.stream().mapToInt(Integer::intValue).sum())
                .divide(BigDecimal.valueOf(medidos.size()), 1, RoundingMode.HALF_UP);
        return new VentanaDelSemaforo(desde, desde.plusDays(6), promedio,
                promedio == null ? ColorSemaforo.SIN_DATOS : colorDe(promedio), medidos.size(), cerrada, dias);
    }

    /** Una ventana en la que los siete días tuvieron el mismo porcentaje. */
    public static VentanaDelSemaforo ventanaPareja(LocalDate desde, boolean cerrada, int porcentaje) {
        Integer[] dias = new Integer[SemanaDelSemaforo.DIAS];
        Arrays.fill(dias, porcentaje);
        return ventana(desde, cerrada, dias);
    }

    /** Los umbrales del contrato (§1): verde ≥ 80, amarillo 60 a 79,9, rojo &lt; 60. */
    public static ColorSemaforo colorDe(BigDecimal porcentaje) {
        if (porcentaje.compareTo(BigDecimal.valueOf(80)) >= 0) {
            return ColorSemaforo.VERDE;
        }
        return porcentaje.compareTo(BigDecimal.valueOf(60)) >= 0 ? ColorSemaforo.AMARILLO : ColorSemaforo.ROJO;
    }

    private static DiaDelSemaforo dia(LocalDate fecha, Integer porcentaje) {
        if (porcentaje == null) {
            return DiaDelSemaforo.sinPorcentaje(fecha, EstadoDiaSemaforo.SIN_DATOS);
        }
        if (porcentaje % 5 != 0) {
            throw new IllegalArgumentException("Con 20 habitos por dia, el porcentaje va de 5 en 5: " + porcentaje);
        }
        return new DiaDelSemaforo(fecha, EstadoDiaSemaforo.MEDIDO, porcentaje, colorDe(BigDecimal.valueOf(porcentaje)),
                HABITOS_POR_DIA, porcentaje * HABITOS_POR_DIA / 100, 0, 0);
    }
}
