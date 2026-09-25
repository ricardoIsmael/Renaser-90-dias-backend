package com.renaser.os.rag.application.ports.out.academia;

import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Puerto propio de {@code rag} para los cursos del aprendiz y el motivo de un bloqueo
 * (herramientas {@code consultar_mis_cursos} y {@code consultar_por_que_esta_bloqueado}, 2026-09-23).
 *
 * <p>El catalogo es de {@code academy}: el adaptador delega en
 * {@code academy.api.CursosDelAprendizFinder} (D-41). El gate por dia de programa y el criterio de
 * revelar solo el bloqueo por dia los decide {@code academy}; aca no se reinterpreta nada.
 */
public interface ConsultarCursosPort {

    /** @throws RuntimeException cuenta inexistente o suspendida */
    CursosDelAprendiz cursosDe(UserId actorId);

    /** Un curso inexistente o bloqueado por un motivo que no se revela da {@link Bloqueo#bloqueado()} = false. */
    Bloqueo bloqueoDeCurso(UserId actorId, String cursoId);

    /** Idem, partiendo de una leccion (hereda el de su curso, si no el de su seccion). */
    Bloqueo bloqueoDeLeccion(UserId actorId, String leccionId);

    record CursosDelAprendiz(List<CursoAccesible> accesibles, List<CursoBloqueado> bloqueados) {

        public CursosDelAprendiz {
            accesibles = List.copyOf(accesibles);
            bloqueados = List.copyOf(bloqueados);
        }
    }

    record CursoAccesible(String cursoId, String titulo, int totalLecciones, int leccionesCompletadas) {
    }

    record CursoBloqueado(String cursoId, String titulo, int diaDesbloqueo, int diaProgramaActual) {
    }

    /** {@code titulo}, {@code diaDesbloqueo} y {@code diaProgramaActual} son {@code null} si no esta bloqueado. */
    record Bloqueo(boolean bloqueado, String titulo, Integer diaDesbloqueo, Integer diaProgramaActual) {
    }
}
