package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Una obligación de hábito de un día concreto, con la programación que tenía ESE día.
 *
 * <p>Sale de {@code registros_habito}, que es un snapshot histórico y no una proyección del
 * catálogo de hoy: guarda su propio {@code dia_programa} y {@code tipo_dia}. Es la única forma
 * de que reprogramar un hábito hoy no reescriba la semana pasada del alumno (plan.md §7).
 *
 * @param registroId        identidad de la obligación. Es lo que deduplica una entrega.
 * @param requiereEvidencia si el hábito exigía archivo. No todo hábito se acredita subiendo algo.
 * @param opcional          si ese día no era exigible. No entra al denominador de la evaluación.
 */
public record ObligacionHabito(UUID registroId, UserId participanteId, LocalDate fecha, int diaPrograma,
                                String titulo, EstadoObligacion estado, boolean requiereEvidencia,
                                boolean opcional) {

    /** Cuenta para la evaluación: exigía evidencia y no era opcional. */
    public boolean exigible() {
        return requiereEvidencia && !opcional;
    }
}
