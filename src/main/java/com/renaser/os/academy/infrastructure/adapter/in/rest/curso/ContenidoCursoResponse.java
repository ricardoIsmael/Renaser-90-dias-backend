package com.renaser.os.academy.infrastructure.adapter.in.rest.curso;

import com.renaser.os.academy.application.ports.in.curso.ConsultarCursoDetalleUseCase.ContenidoCurso;

import java.util.List;

/** Espejo de `ContenidoCurso` (`src/types/cursos.ts`) — `sueltas`/`secciones` son ya nombres validos en ambos idiomas. */
public record ContenidoCursoResponse(List<LeccionLiteResponse> sueltas, List<SeccionConLeccionesResponse> secciones) {

    public static ContenidoCursoResponse from(ContenidoCurso contenido) {
        return new ContenidoCursoResponse(contenido.sueltas().stream().map(LeccionLiteResponse::from).toList(),
                contenido.secciones().stream().map(SeccionConLeccionesResponse::from).toList());
    }
}
