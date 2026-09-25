package com.renaser.os.academy.api;

import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Contrato publico de {@code academy}: los cursos de una persona y por que uno esta bloqueado
 * (2026-09-23). Primer consumidor: el acompanante de {@code rag} ({@code consultar_mis_cursos} y
 * {@code consultar_por_que_esta_bloqueado}).
 *
 * <p>Solo lectura y sin reglas propias: delega en {@code ConsultarMisCursosUseCase},
 * {@code ConsultarCursosBloqueadosUseCase}, {@code ConsultarMotivoBloqueoCursoUseCase} y
 * {@code ConsultarMotivoBloqueoLeccionUseCase}, los de {@code GET /api/v1/cursos},
 * {@code /cursos/bloqueados}, {@code /cursos/{id}/preview} y {@code /lecciones/{id}/preview}. El
 * gate por rol, publicacion y dia de programa sigue en esos casos de uso, igual que el criterio
 * de <b>no revelar de mas</b>: el unico motivo que se cuenta es el dia de desbloqueo.
 */
public interface CursosDelAprendizFinder {

    /** @throws RuntimeException si la cuenta no existe o esta suspendida */
    CursosDelAprendiz cursosDe(UserId actorId);

    /** Un curso inexistente, o bloqueado por un motivo que no se revela, da {@link MotivoBloqueo#noBloqueado()}. */
    MotivoBloqueo motivoBloqueoCurso(UserId actorId, String cursoId);

    /** La leccion hereda el bloqueo de su curso; si no, el de su seccion. Mismo criterio de no revelar. */
    MotivoBloqueo motivoBloqueoLeccion(UserId actorId, String leccionId);

    /** @param bloqueados solo los que se desbloquean por dia de programa (vacio fuera de TRAINEE) */
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

    /**
     * @param titulo            del curso o la leccion bloqueada; {@code null} si no esta bloqueado
     * @param diaDesbloqueo     {@code null} si no esta bloqueado
     * @param diaProgramaActual {@code null} si no esta bloqueado
     */
    record MotivoBloqueo(boolean bloqueado, String titulo, Integer diaDesbloqueo, Integer diaProgramaActual) {

        public static MotivoBloqueo noBloqueado() {
            return new MotivoBloqueo(false, null, null, null);
        }
    }
}
