package com.renaser.os.chat.infrastructure.adapter.in.rest.miembro;

import com.renaser.os.chat.application.ports.in.miembro.PaginaMiembros;

import java.util.List;

public record MiembrosPageResponse(List<MiembroResponse> members, String nextCursor) {

    public static MiembrosPageResponse from(PaginaMiembros pagina) {
        return new MiembrosPageResponse(pagina.miembros().stream().map(MiembroResponse::from).toList(),
                pagina.siguienteCursor() != null ? pagina.siguienteCursor().toString() : null);
    }
}
