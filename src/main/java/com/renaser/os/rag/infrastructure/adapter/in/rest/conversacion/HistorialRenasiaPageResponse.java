package com.renaser.os.rag.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.rag.application.ports.in.conversacion.ObtenerHistorialUseCase.PaginaMensajesRenasia;

import java.util.List;

public record HistorialRenasiaPageResponse(List<MensajeRenasiaResponse> messages, String nextCursor,
                                            boolean hasMore) {

    public static HistorialRenasiaPageResponse from(PaginaMensajesRenasia pagina) {
        return new HistorialRenasiaPageResponse(pagina.mensajes().stream().map(MensajeRenasiaResponse::from).toList(),
                pagina.siguienteCursor() != null ? pagina.siguienteCursor().toString() : null, pagina.hayMas());
    }
}
