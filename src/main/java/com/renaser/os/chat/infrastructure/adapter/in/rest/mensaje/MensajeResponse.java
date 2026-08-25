package com.renaser.os.chat.infrastructure.adapter.in.rest.mensaje;

import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;

/** {@code type} en ingles (TEXT/IMAGE/AUDIO/VIDEO/SYSTEM) — la app publicada nunca ve
 * `tipo_mensaje` en espanol; la traduccion (D-36) vive solo aca. */
public record MensajeResponse(String id, String conversationId, String senderId, String type, String text,
                               String mediaBucket, String mediaPath, String mediaMime, Integer mediaBytes,
                               Short mediaDurationSeconds, boolean hidden, String replyToId, String createdAt) {

    public static MensajeResponse from(Mensaje m) {
        return new MensajeResponse(m.id().toString(), m.conversacionId().toString(), m.emisorId().toString(),
                toWireTipo(m.tipo()), m.texto(), m.mediaBucket(), m.mediaRuta(), m.mediaMime(), m.mediaBytes(),
                m.mediaDuracionS(), m.oculto(), m.respuestaAId() != null ? m.respuestaAId().toString() : null,
                m.creadoEn().toString());
    }

    static String toWireTipo(TipoMensaje tipo) {
        return switch (tipo) {
            case TEXTO -> "TEXT";
            case IMAGEN -> "IMAGE";
            case AUDIO -> "AUDIO";
            case VIDEO -> "VIDEO";
            case SISTEMA -> "SYSTEM";
        };
    }
}
