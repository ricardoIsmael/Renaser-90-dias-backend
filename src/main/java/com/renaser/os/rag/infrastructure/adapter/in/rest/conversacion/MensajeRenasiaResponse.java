package com.renaser.os.rag.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.rag.domain.model.conversacion.FuenteMensaje;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.RolMensaje;

import java.util.List;

/** {@code role} en ingles (USER/ASSISTANT) — mismo criterio que {@code chat} para el
 * enum de tipo de mensaje (D-36): la app publicada nunca ve los nombres en espanol. */
public record MensajeRenasiaResponse(String id, String role, String content, List<String> sourceLessonIds,
                                      String createdAt) {

    public static MensajeRenasiaResponse from(MensajeRenasia m) {
        return new MensajeRenasiaResponse(m.id().toString(), toWireRol(m.rol()), m.contenido(),
                m.fuentes().stream().map(FuenteMensaje::leccionId).toList(), m.creadoEn().toString());
    }

    static String toWireRol(RolMensaje rol) {
        return switch (rol) {
            case USUARIO -> "USER";
            case ASISTENTE -> "ASSISTANT";
        };
    }
}
