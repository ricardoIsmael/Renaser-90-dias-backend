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
 *
 * @param tipo   de dónde sale; la agenda personal no es un item sino un tramo suelto y no pasa por acá
 * @param titulo cómo lo llama la persona; es lo que lleva su bloque en la agenda del día
 */
public record ItemPlanificado(LocalDateTime inicio, int minutos, TipoDeOcupado tipo, String titulo) {

    public ItemPlanificado {
        if (minutos < 1) {
            throw new IllegalArgumentException("Un item dura al menos un minuto: " + minutos);
        }
        if (tipo == null || tipo == TipoDeOcupado.AGENDA) {
            throw new IllegalArgumentException("Un item es un habito, una accion o un evento: " + tipo);
        }
    }

    public static ItemPlanificado deHabito(LocalDate dia, LocalTime hora, String titulo) {
        return new ItemPlanificado(dia.atTime(hora), ReglasDeHuecos.MINUTOS_DE_UN_HABITO, TipoDeOcupado.HABITO, titulo);
    }

    /** La hora de fin anterior a la de inicio (o igual) no se rechaza: se usa la duración por defecto. */
    public static ItemPlanificado deAccion(LocalDate dia, LocalTime inicio, LocalTime fin, String titulo) {
        boolean finValido = fin != null && fin.isAfter(inicio);
        int minutos = finValido ? (int) Duration.between(inicio, fin).toMinutes()
                : ReglasDeHuecos.MINUTOS_DE_UNA_ACCION;
        return new ItemPlanificado(dia.atTime(inicio), minutos, TipoDeOcupado.ACCION, titulo);
    }

    /** Un evento sin duración (o con el fin antes del inicio) dura lo que fija {@link ReglasDeHuecos}. */
    public static ItemPlanificado deEvento(LocalDateTime inicia, LocalDateTime termina, String titulo) {
        boolean finValido = termina != null && termina.isAfter(inicia);
        int minutos = finValido ? (int) Duration.between(inicia, termina).toMinutes()
                : ReglasDeHuecos.MINUTOS_DE_UN_EVENTO;
        return new ItemPlanificado(inicia, minutos, TipoDeOcupado.EVENTO, titulo);
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

    /** Lo mismo que {@link #tramoEn}, con su tipo y su nombre. */
    public Optional<BloqueOcupado> bloqueEn(LocalDate dia) {
        return tramoEn(dia).map(tramo -> new BloqueOcupado(tramo, tipo, titulo));
    }
}
