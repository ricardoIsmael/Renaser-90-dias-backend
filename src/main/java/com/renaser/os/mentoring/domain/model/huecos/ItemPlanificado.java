package com.renaser.os.mentoring.domain.model.huecos;

import com.renaser.os.shared.domain.TramoDelDia;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Algo de la persona con hora que ocupa tiempo: un hábito, una acción del día o un evento. Tiene el instante local en
 * que empieza y cuántos minutos dura (la propia o la que fija {@link ReglasDeHuecos}).
 *
 * <p>Lo que cruza la medianoche se parte: cada día cuenta su pedazo ({@link #tramoEn}). Un hábito nunca cruza (D-122),
 * pero una acción por defecto de 30 minutos que empieza a las 23:50 sí.
 */
public record ItemPlanificado(LocalDateTime inicio, int minutos) {

    public ItemPlanificado {
        if (minutos < 1) {
            throw new IllegalArgumentException("Un item dura al menos un minuto: " + minutos);
        }
    }

    public static ItemPlanificado deHabito(LocalDate dia, LocalTime hora) {
        return new ItemPlanificado(dia.atTime(hora), ReglasDeHuecos.MINUTOS_DE_UN_HABITO);
    }

    /** La hora de fin anterior a la de inicio (o igual) no se rechaza: se usa la duración por defecto. */
    public static ItemPlanificado deAccion(LocalDate dia, LocalTime inicio, LocalTime fin) {
        boolean finValido = fin != null && fin.isAfter(inicio);
        int minutos = finValido ? (int) Duration.between(inicio, fin).toMinutes()
                : ReglasDeHuecos.MINUTOS_DE_UNA_ACCION;
        return new ItemPlanificado(dia.atTime(inicio), minutos);
    }

    /** Un evento sin duración (o con el fin antes del inicio) dura lo que fija {@link ReglasDeHuecos}. */
    public static ItemPlanificado deEvento(LocalDateTime inicia, LocalDateTime termina) {
        boolean finValido = termina != null && termina.isAfter(inicia);
        int minutos = finValido ? (int) Duration.between(inicia, termina).toMinutes()
                : ReglasDeHuecos.MINUTOS_DE_UN_EVENTO;
        return new ItemPlanificado(inicia, minutos);
    }

    /** El pedazo de este item que cae en {@code dia}, en minutos de ese día; vacío si no lo toca. */
    public Optional<TramoDelDia> tramoEn(LocalDate dia) {
        LocalDateTime medianoche = dia.atStartOfDay();
        LocalDateTime siguiente = medianoche.plusDays(1);
        LocalDateTime desde = inicio.isAfter(medianoche) ? inicio : medianoche;
        LocalDateTime fin = inicio.plusMinutes(minutos);
        LocalDateTime hasta = fin.isBefore(siguiente) ? fin : siguiente;
        if (!desde.isBefore(hasta)) {
            return Optional.empty();
        }
        int minutoDesde = (int) Duration.between(medianoche, desde).toMinutes();
        int minutoHasta = (int) Duration.between(medianoche, hasta).toMinutes();
        return Optional.of(new TramoDelDia(minutoDesde, minutoHasta));
    }
}
