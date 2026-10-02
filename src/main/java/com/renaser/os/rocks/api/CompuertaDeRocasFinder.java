package com.renaser.os.rocks.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

/**
 * Contrato publico de {@code rocks} para PREGUNTAR, antes de proponer, si la persona puede planificar
 * rocas (D-247, E-496). El acompanante ({@code rag}) le propuso a una persona sin Rocas Maestras un plan
 * que despues fallo al confirmar con {@code ROCKS_LOCKED}: no tenia como saberlo.
 *
 * <p><b>Delega, no reimplementa.</b> Responde con la MISMA regla que rechaza la escritura:
 * {@code RocasMaestras.completas()} para {@code ROCKS_LOCKED} y el objetivo semanal de la semana de esa
 * fecha ({@code AccesoARocas.objetivoSemanalDelDia}) para {@code NO_WEEKLY_ROCK}. Si cambia la regla,
 * cambian las dos cosas a la vez.
 *
 * <p>No decide si la fecha se puede planificar (ventana, dia en curso): eso sigue rechazandose al crear,
 * con su propio motivo.
 */
public interface CompuertaDeRocasFinder {

    /**
     * {@code false} = le faltan Rocas Maestras: todo plan, semanal o diario, vuelve con {@code ROCKS_LOCKED}.
     * Solo lee: no exige cuenta activa (la escritura lo sigue exigiendo).
     */
    boolean rocasMaestrasCompletas(UserId aprendizId);

    /**
     * Los ejes ({@code CUERPO}, {@code TRABAJO}, {@code RELACIONES}) que NO tienen objetivo semanal en la
     * semana de esa fecha: una accion de ese dia en esos ejes vuelve con {@code NO_WEEKLY_ROCK}. Sin Rocas
     * Maestras, los tres. Vacio si la persona todavia no eligio su Dia 1 (eso lo rechaza la fecha).
     *
     * @throws java.util.NoSuchElementException si el participante no existe
     */
    List<String> ejesSinObjetivoSemanal(UserId aprendizId, LocalDate fecha);
}
