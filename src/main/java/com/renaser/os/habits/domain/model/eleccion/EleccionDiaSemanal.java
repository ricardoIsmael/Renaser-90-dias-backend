package com.renaser.os.habits.domain.model.eleccion;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * El dia que el aprendiz eligio, DE ESA SEMANA, para un habito de eleccion semanal (tabla
 * `dias_semanales_habito`) — hoy el unico es "Dia sin celular" (weeklyChoice.ts). Se guarda
 * la FECHA resuelta, no el numero de dia de la semana — evita re-resolver contra el ancla
 * y la timezone cada vez que se lee.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = {"participanteId", "habitoId", "fechaEjecucion"})
public final class EleccionDiaSemanal {

    private final UserId participanteId;
    private final HabitoId habitoId;
    private final LocalDate fechaEjecucion;
    /** El lunes de la semana en que se eligio (WEEK_ANCHOR=MONDAY, weeklyChoice.ts). */
    private final LocalDate semanaInicio;
    private final Instant creadoEn;

    public static EleccionDiaSemanal elegir(UserId participanteId, HabitoId habitoId, LocalDate fechaEjecucion,
                                             LocalDate semanaInicio, Instant ahora) {
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        Objects.requireNonNull(habitoId, "habitoId es obligatorio");
        Objects.requireNonNull(fechaEjecucion, "fechaEjecucion es obligatoria");
        Objects.requireNonNull(semanaInicio, "semanaInicio es obligatoria");
        if (fechaEjecucion.isBefore(semanaInicio) || fechaEjecucion.isAfter(semanaInicio.plusDays(6))) {
            throw new IllegalArgumentException("fechaEjecucion debe caer dentro de la semana elegida");
        }
        return new EleccionDiaSemanal(participanteId, habitoId, fechaEjecucion, semanaInicio, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static EleccionDiaSemanal rehydrate(UserId participanteId, HabitoId habitoId, LocalDate fechaEjecucion,
                                                LocalDate semanaInicio, Instant creadoEn) {
        return new EleccionDiaSemanal(participanteId, habitoId, fechaEjecucion, semanaInicio, creadoEn);
    }

    @Override
    public String toString() {
        return "EleccionDiaSemanal[" + participanteId + ", " + habitoId + ", " + fechaEjecucion + "]";
    }
}
