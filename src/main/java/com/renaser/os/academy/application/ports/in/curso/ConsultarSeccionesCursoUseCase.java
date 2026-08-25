package com.renaser.os.academy.application.ports.in.curso;

import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/** GET /api/v1/cursos/{id}/secciones — mismo arbol que `ConsultarCursoDetalleUseCase`, solo las secciones. */
public interface ConsultarSeccionesCursoUseCase {

    List<ConsultarCursoDetalleUseCase.SeccionConLecciones> secciones(UserId actorId, CursoId cursoId);
}
