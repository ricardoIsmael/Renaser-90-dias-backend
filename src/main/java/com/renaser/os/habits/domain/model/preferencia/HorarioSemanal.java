package com.renaser.os.habits.domain.model.preferencia;

import java.time.DayOfWeek;
import java.util.Objects;

/**
 * La hora de un habito para UN dia de la semana, que se repite todas las semanas.
 *
 * <p>Hermana de {@link HorarioPorFecha} y distinta a proposito: aquella es una excepcion de una
 * FECHA y no se repite ("el lunes 8 a las 5"); esta es un patron ("los lunes a las 5"). Las dos
 * conviven y la de fecha exacta gana, porque es la mas especifica.
 *
 * <p>No lleva "activo": apagar un dia ya se puede por fecha desde V38, y dos mecanismos para lo
 * mismo serian dos respuestas posibles a "va hoy?".
 */
public record HorarioSemanal(DayOfWeek diaSemana, PreferenciaHorario preferencia) {

    public HorarioSemanal {
        Objects.requireNonNull(diaSemana, "diaSemana es obligatorio");
        Objects.requireNonNull(preferencia, "preferencia es obligatoria");
        // Sin hora de disparo esta fila no dice nada que la preferencia general no diga ya. Para
        // volver al horario general se BORRA, no se vacia.
        Objects.requireNonNull(preferencia.horaDisparo(), "horaDisparo es obligatoria");
        if (preferencia.horaLimite() != null && !preferencia.horaDisparo().isBefore(preferencia.horaLimite())) {
            throw new IllegalArgumentException("horaLimite debe ser posterior a horaDisparo");
        }
    }
}
