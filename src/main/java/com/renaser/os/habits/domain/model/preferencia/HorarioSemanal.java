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
 * <p><b>Ampliado 2026-09-40 (V40).</b> Tambien puede APAGAR el dia. La version anterior no lo
 * traia con el argumento de que apagar ya se podia por fecha (V38), y ese razonamiento confundia
 * dos preguntas distintas: "este martes no" no es "los martes nunca", y ninguna de las dos tablas
 * puede expresar la otra.
 *
 * <p>Con {@code activo = false} la hora deja de ser obligatoria: una fila que solo apaga el dia no
 * necesita hora, y exigirla congelaria el horario de ese dia si despues cambia el general.
 */
public record HorarioSemanal(DayOfWeek diaSemana, PreferenciaHorario preferencia, boolean activo) {

    public HorarioSemanal {
        Objects.requireNonNull(diaSemana, "diaSemana es obligatorio");
        Objects.requireNonNull(preferencia, "preferencia es obligatoria");
        if (activo) {
            // Sin hora de disparo una fila ENCENDIDA no dice nada que la preferencia general no
            // diga ya. Para volver al horario general se BORRA, no se vacia.
            Objects.requireNonNull(preferencia.horaDisparo(), "horaDisparo es obligatoria");
        }
        if (preferencia.horaDisparo() != null && preferencia.horaLimite() != null
                && !preferencia.horaDisparo().isBefore(preferencia.horaLimite())) {
            throw new IllegalArgumentException("horaLimite debe ser posterior a horaDisparo");
        }
    }

    /** Firma historica (V39): una hora propia siempre dejaba el dia encendido. */
    public HorarioSemanal(DayOfWeek diaSemana, PreferenciaHorario preferencia) {
        this(diaSemana, preferencia, true);
    }
}
