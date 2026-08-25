package com.renaser.os.chat.infrastructure.adapter.in.rest.mensaje;

import com.renaser.os.chat.application.ports.in.mensaje.ListarMensajesUseCase.PaginaMensajes;

import java.util.List;

public record MensajesPageResponse(List<MensajeResponse> messages, String nextCursor, boolean hasMore) {

    public static MensajesPageResponse from(PaginaMensajes pagina) {
        return new MensajesPageResponse(pagina.mensajes().stream().map(MensajeResponse::from).toList(),
                pagina.siguienteCursor() != null ? pagina.siguienteCursor().toString() : null, pagina.hayMas());
    }
}
