package com.renaser.os.points.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Conteo diario de objetivos del día (filas de {@code rocas_diarias}, las «acciones del día» de la
 * app) EN LOTE para el semáforo del aprendiz (D-168). Lo implementa {@code rocks} reutilizando la
 * consulta agrupada de D-43 ({@code CargarConteoDiarioRocasPort}). Mismo motivo DIP que
 * {@link PorcentajeRocasFinder}.
 *
 * <p>Semántica de cada {@link ConteoDelDia}: {@code programados} = objetivos planificados ese día
 * (un objetivo nunca es opcional, así que todos cuentan); {@code cumplidos} = los que tienen
 * {@code completada = true}. Los pasos de un objetivo ({@code acciones_diarias}, V61) no cuentan
 * aparte. Un día sin objetivos no aparece.
 */
public interface ConteoDiarioObjetivosFinder {

    /** Mismo contrato de parámetros y retorno que {@link ConteoDiarioHabitosFinder}. */
    Map<UserId, List<ConteoDelDia>> porParticipanteEntre(Collection<UserId> participantes, LocalDate desde,
                                                         LocalDate hasta);
}
