package com.renaser.os.rocks.domain.model.coherencia;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * <b>La coherencia</b>: que porcentaje de las acciones diarias que se planifico para la semana
 * termino cumpliendo. Regla confirmada por el dueño del proyecto el 2026-09-15 (D-128).
 *
 * <p><b>Se cuentan acciones, no dias.</b> La suma de completadas sobre la suma de planificadas en
 * la ventana. Un dia con tres acciones pesa el triple que uno con una, porque se planificaron el
 * triple de cosas.
 *
 * <blockquote><b>Corregido el 2026-09-15.</b> Hasta hoy esto promediaba el PORCENTAJE DE CADA DIA
 * —un dia de 1/1 valia lo mismo que uno de 3/3— y sobre todo devolvia <b>100.0 cuando no habia
 * ningun dato</b>. Eso ultimo es lo que hacia que la app le dijera "100 % · Nivel de excelencia" a
 * cualquiera, incluso a quien nunca planifico una accion: la ventana vacia se leia como semana
 * perfecta. Ahora una ventana sin acciones no es un 100, es <b>sin dato</b>.</blockquote>
 *
 * <p>La diferencia entre "no planifico nada" y "planifico y no cumplio" no es un detalle de
 * presentacion: el segundo es un 0 % que hay que mirar, y el primero es alguien que todavia no
 * armo su semana. Mostrarlos iguales —de cualquiera de los dos lados— es mentir.
 */
public final class PorcentajeRocas {

    private static final BigDecimal CIEN = new BigDecimal("100.0");

    private PorcentajeRocas() {
    }

    /**
     * @param dias los dias CON al menos una accion planificada (ver {@code DiaRocas}: un dia sin
     *             ninguna no se reporta)
     * @return el porcentaje con un decimal, o {@link Optional#empty()} si en toda la ventana no
     *         hubo una sola accion planificada — no hay de que calcular un porcentaje
     */
    public static Optional<BigDecimal> calcular(List<DiaRocas> dias) {
        if (dias == null || dias.isEmpty()) {
            return Optional.empty();
        }
        int planificadas = dias.stream().mapToInt(DiaRocas::total).sum();
        int cumplidas = dias.stream().mapToInt(DiaRocas::completadas).sum();
        if (planificadas == 0) {
            return Optional.empty();
        }
        BigDecimal porcentaje = BigDecimal.valueOf(cumplidas)
                .multiply(CIEN)
                .divide(BigDecimal.valueOf(planificadas), 1, RoundingMode.HALF_UP);
        return Optional.of(porcentaje);
    }
}
