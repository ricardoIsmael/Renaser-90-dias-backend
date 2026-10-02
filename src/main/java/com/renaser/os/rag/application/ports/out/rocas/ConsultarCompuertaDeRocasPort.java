package com.renaser.os.rag.application.ports.out.rocas;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

/**
 * Si la persona puede planificar rocas, ANTES de proponerle nada (D-247, E-496). Lo resuelve {@code rocks}
 * ({@code rocks.api.CompuertaDeRocasFinder}) con la misma regla que rechaza la escritura: el acompanante no
 * la copia, la pregunta.
 */
public interface ConsultarCompuertaDeRocasPort {

    /** {@code false} = le faltan Rocas Maestras: todo plan vuelve con {@code ROCAS_BLOQUEADAS}. */
    boolean rocasMaestrasCompletas(UserId aprendizId);

    /** Los ejes sin objetivo semanal en la semana de esa fecha; un plan de ese dia en ellos se rechazaria. */
    List<String> ejesSinObjetivoSemanal(UserId aprendizId, LocalDate fecha);
}
