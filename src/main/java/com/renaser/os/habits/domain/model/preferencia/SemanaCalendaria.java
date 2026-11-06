package com.renaser.os.habits.domain.model.preferencia;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Una semana de lunes a domingo, contada con fechas locales de la persona (spec 028). Es la unidad de «Solo esta semana»
 * y de repetir una semana en la siguiente: las fechas ya vienen en la zona del participante, asi que aca no hay zonas.
 *
 * <p>No es la «semana de programa» de {@link CuotaEdicionHorario} (que arranca el dia 1 del programa, sea el dia que sea) ni
 * el numero de semana de {@code rocks} (D-203): esas cuentan de otra manera y para otra cosa.
 */
public record SemanaCalendaria(LocalDate lunes) {

    public SemanaCalendaria {
        Objects.requireNonNull(lunes, "lunes es obligatorio");
        if (lunes.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("La semana tiene que empezar un lunes: " + lunes);
        }
    }

    /** La semana que contiene a esa fecha. */
    public static SemanaCalendaria de(LocalDate fecha) {
        return new SemanaCalendaria(fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
    }

    /** La semana que empieza ese lunes; 400 si la fecha no es lunes. */
    public static SemanaCalendaria desde(LocalDate lunes) {
        return new SemanaCalendaria(lunes);
    }

    public LocalDate domingo() {
        return lunes.plusDays(6);
    }

    /** La fecha del dia {@code indice} de la semana (0 = lunes ... 6 = domingo). */
    public LocalDate fecha(int indice) {
        if (indice < 0 || indice > 6) {
            throw new IllegalArgumentException("Un día de la semana va de 0 a 6: " + indice);
        }
        return lunes.plusDays(indice);
    }

    public List<LocalDate> fechas() {
        return IntStream.rangeClosed(0, 6).mapToObj(lunes::plusDays).toList();
    }

    public boolean contiene(LocalDate fecha) {
        return !fecha.isBefore(lunes) && !fecha.isAfter(domingo());
    }

    public SemanaCalendaria siguiente() {
        return new SemanaCalendaria(lunes.plusDays(7));
    }

    /** La fecha que ocupa, en esta semana, el mismo dia de la semana que {@code fechaDeOtra} en {@code otra}. */
    public LocalDate elMismoDiaQue(LocalDate fechaDeOtra, SemanaCalendaria otra) {
        return lunes.plusDays(java.time.temporal.ChronoUnit.DAYS.between(otra.lunes, fechaDeOtra));
    }

    /** {@code true} si alguna fecha de la semana es posterior a {@code hoy}: todavia queda algo que se pueda cambiar. */
    public boolean tieneDiasPorCambiar(LocalDate hoy) {
        return domingo().isAfter(hoy);
    }
}
