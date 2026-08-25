package com.renaser.os.academy.application.ports.in.curso;

import com.renaser.os.academy.domain.model.curso.Curso;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.Leccion;
import com.renaser.os.academy.domain.model.curso.SeccionCurso;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * GET /api/v1/cursos/{id} — arbol de un curso (secciones + lecciones livianas)
 * con la progresion por dia ya aplicada. Espejo de `getCursoDetalle` +
 * `aplicarProgresionDeSecciones` (RenaserBack `service.ts:19-106`).
 */
public interface ConsultarCursoDetalleUseCase {

    CursoDetalle detalle(UserId actorId, CursoId cursoId);

    record CursoDetalle(Curso curso, ContenidoCurso contenido, String portadaFirmada) {
    }

    record ContenidoCurso(List<LeccionConProgresion> sueltas, List<SeccionConLecciones> secciones) {
    }

    record SeccionConLecciones(SeccionCurso seccion, boolean bloqueadaPorDia, Integer programDayActual,
                                int diasFaltantes, List<LeccionConProgresion> lecciones) {
    }

    /**
     * Version liviana de una leccion para el arbol del curso — el gate de
     * dia SIEMPRE viene heredado de la seccion (o ausente si es suelta),
     * nunca de la leccion misma (repository.ts:236-238 del repo viejo).
     */
    record LeccionConProgresion(Leccion leccion, int recursosCount, Integer diaDesbloqueo, boolean bloqueadaPorDia,
                                 Integer programDayActual, int diasFaltantes) {
    }
}
