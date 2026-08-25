package com.renaser.os.academy.application.ports.out.progreso;

import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface LoadProgresoLeccionPort {

    /** Ids de lecciones que el usuario ya completo, en TODOS sus cursos. */
    Set<LeccionId> leccionesCompletadas(UserId usuarioId);

    /** Cuantas lecciones completo el usuario, agrupado por curso — insumo de `completadas` en el progreso. */
    Map<CursoId, Integer> completadasPorCurso(UserId usuarioId);

    /**
     * Version EN LOTE de {@link #completadasPorCurso(UserId)} para MUCHOS
     * usuarios de una sola consulta — existe por D-43 (`PorcentajeCursosFinder`):
     * el calculo de "cursosPct" para el Ranking General no puede hacer una
     * consulta por aprendiz sin reproducir el incidente real documentado en
     * `prisma/migrations/general_ranking_scores_function.sql` (RenaserBack,
     * "Too many database connections opened" con ~30 cuentas activas).
     */
    Map<UserId, Map<CursoId, Integer>> completadasPorCursoEnLote(Collection<UserId> usuarios);

    boolean estaCompletada(UserId usuarioId, LeccionId leccionId);
}
