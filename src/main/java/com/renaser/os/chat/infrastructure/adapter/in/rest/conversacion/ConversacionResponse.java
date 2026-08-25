package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;

/** {@code type} en ingles (CELL/DIRECT/GLOBAL) — la app publicada nunca ve
 * `tipo_conversacion` en espanol; la traduccion (D-36) vive solo aca. */
public record ConversacionResponse(String id, String type, String celulaId, String nombre, String createdAt) {

    public static ConversacionResponse from(Conversacion c) {
        return new ConversacionResponse(c.id().toString(), toWireTipo(c.tipo()),
                c.celulaId() != null ? c.celulaId().toString() : null, c.nombre(), c.creadoEn().toString());
    }

    static String toWireTipo(TipoConversacion tipo) {
        return switch (tipo) {
            case CELULA -> "CELL";
            case DIRECTA -> "DIRECT";
            case GLOBAL -> "GLOBAL";
        };
    }
}
