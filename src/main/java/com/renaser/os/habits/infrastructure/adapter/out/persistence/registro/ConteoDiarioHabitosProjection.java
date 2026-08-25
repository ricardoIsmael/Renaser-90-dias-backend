package com.renaser.os.habits.infrastructure.adapter.out.persistence.registro;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Proyeccion Spring Data de una fila agregada (participante, dia) de la
 * consulta en lote — ver {@link SpringDataRegistroHabitoRepository#contarPorParticipanteYDiaEnRango}.
 * Agregacion pura de conteos; ninguna decision de negocio vive aca.
 */
interface ConteoDiarioHabitosProjection {

    UUID getParticipanteId();

    LocalDate getFecha();

    long getTotalRegistros();

    long getCompletados();

    long getOpcionalesNoCompletados();
}
