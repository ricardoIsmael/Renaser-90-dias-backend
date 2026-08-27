package com.renaser.os.chat.infrastructure.adapter.in.rest.mensaje;

import com.renaser.os.chat.application.ports.in.mensaje.MensajeEnriquecido;
import com.renaser.os.chat.application.ports.in.mensaje.MensajeEnriquecido.RespuestaPreview;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;

/** {@code type} en ingles (TEXT/IMAGE/AUDIO/VIDEO/SYSTEM) — la app publicada nunca ve
 * `tipo_mensaje` en espanol; la traduccion (D-36) vive solo aca.
 *
 * <p>{@code senderName}/{@code senderAvatarUrl} y {@code replyTo} (#29) solo vienen
 * resueltos cuando se construye desde un {@link MensajeEnriquecido} — el listado de
 * mensajes (`GET .../messages`). El overload que recibe un {@link Mensaje} crudo (usado
 * hoy solo para el "ultimo mensaje" de {@code ConversacionResumenResponse}) los deja en
 * {@code null}: esa pantalla no los necesita (el frontend real no los pide en
 * {@code ConversationSummary.lastMessage}). */
public record MensajeResponse(String id, String conversationId, String senderId, String senderName,
                               String senderAvatarUrl, String type, String text, String mediaBucket,
                               String mediaPath, String mediaMime, Integer mediaBytes,
                               Short mediaDurationSeconds, boolean hidden, String replyToId,
                               ReplyPreviewResponse replyTo, String createdAt) {

    public static MensajeResponse from(Mensaje m) {
        return new MensajeResponse(m.id().toString(), m.conversacionId().toString(), m.emisorId().toString(), null,
                null, toWireTipo(m.tipo()), m.texto(), m.mediaBucket(), m.mediaRuta(), m.mediaMime(), m.mediaBytes(),
                m.mediaDuracionS(), m.oculto(), m.respuestaAId() != null ? m.respuestaAId().toString() : null, null,
                m.creadoEn().toString());
    }

    public static MensajeResponse from(MensajeEnriquecido enriquecido) {
        Mensaje m = enriquecido.mensaje();
        return new MensajeResponse(m.id().toString(), m.conversacionId().toString(), m.emisorId().toString(),
                enriquecido.nombreEmisor(), enriquecido.avatarEmisor(), toWireTipo(m.tipo()), m.texto(),
                m.mediaBucket(), m.mediaRuta(), m.mediaMime(), m.mediaBytes(), m.mediaDuracionS(), m.oculto(),
                m.respuestaAId() != null ? m.respuestaAId().toString() : null,
                ReplyPreviewResponse.from(enriquecido.respuestaPreview()), m.creadoEn().toString());
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

    /** Preview del mensaje original citado — {@code text} son solo los primeros
     * caracteres, no el mensaje completo (ver {@link MensajeEnriquecido#LARGO_PREVIEW}). */
    public record ReplyPreviewResponse(String id, String senderName, String type, String text, String deletedAt) {

        static ReplyPreviewResponse from(RespuestaPreview preview) {
            if (preview == null) {
                return null;
            }
            return new ReplyPreviewResponse(preview.id().toString(), preview.nombreEmisor(),
                    toWireTipo(preview.tipo()), preview.previewTexto(),
                    preview.eliminadoEn() != null ? preview.eliminadoEn().toString() : null);
        }
    }
}
