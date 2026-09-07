package com.renaser.os.habits.domain.model.preferencia;

import java.time.LocalDate;
import java.util.Objects;

/** Excepcion puntual: no cambia la preferencia general ni otra fecha del mismo habito. */
public record HorarioPorFecha(LocalDate fecha, PreferenciaHorario preferencia) {
    public HorarioPorFecha {
        Objects.requireNonNull(fecha, "fecha es obligatoria");
        Objects.requireNonNull(preferencia, "preferencia es obligatoria");
        Objects.requireNonNull(preferencia.horaDisparo(), "horaDisparo es obligatoria");
        if (preferencia.horaLimite() != null && !preferencia.horaDisparo().isBefore(preferencia.horaLimite())) {
            throw new IllegalArgumentException("horaLimite debe ser posterior a horaDisparo");
        }
    }

    public static void requirePlanificable(LocalDate fecha, LocalDate hoy) {
        if (!fecha.isAfter(hoy)) {
            throw new IllegalArgumentException("Solo puedes planificar horarios para una fecha futura");
        }
    }
}
