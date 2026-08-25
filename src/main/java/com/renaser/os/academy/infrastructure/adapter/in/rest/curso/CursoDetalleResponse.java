package com.renaser.os.academy.infrastructure.adapter.in.rest.curso;

import com.renaser.os.academy.application.ports.in.curso.ConsultarCursoDetalleUseCase.CursoDetalle;

/**
 * GET /api/v1/cursos/{id} — espejo de `{curso, contenido, portadaFirmada}`
 * (RenaserBack `service.ts: getCursoDetalle`). OJO: a diferencia de
 * {@link MiCursoResponse}, aca `portadaFirmada` es CAMELCASE — asi la
 * devolvia el endpoint viejo (inconsistencia real del wire que ya consume
 * la app, se preserva tal cual). El objeto NO lleva `@JsonNaming`: sus 3
 * claves (`curso`, `contenido`, `portadaFirmada`) ya son las que espera el
 * cliente sin transformar.
 */
public record CursoDetalleResponse(CursoResponse curso, ContenidoCursoResponse contenido, String portadaFirmada) {

    public static CursoDetalleResponse from(CursoDetalle detalle) {
        return new CursoDetalleResponse(CursoResponse.from(detalle.curso()),
                ContenidoCursoResponse.from(detalle.contenido()), detalle.portadaFirmada());
    }
}
