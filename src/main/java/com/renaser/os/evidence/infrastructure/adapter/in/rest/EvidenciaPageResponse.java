package com.renaser.os.evidence.infrastructure.adapter.in.rest;

import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase.PaginaEvidencias;

import java.util.List;

/** Página de {@link EvidenciaResponse} + cursor de la siguiente — compartida por
 * {@code EvidenciaController} (hueco #19) y {@code EvidenciaAdminController} (hueco
 * #20), mismo formato que {@code community.WallFeedPageResponse}. */
public record EvidenciaPageResponse(List<EvidenciaResponse> evidencias, String nextCursor) {

    public static EvidenciaPageResponse from(PaginaEvidencias pagina) {
        return new EvidenciaPageResponse(pagina.evidencias().stream().map(EvidenciaResponse::from).toList(),
                pagina.siguienteCursor() != null ? pagina.siguienteCursor().toString() : null);
    }
}
