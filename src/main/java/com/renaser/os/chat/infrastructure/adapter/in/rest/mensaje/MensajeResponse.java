package com.renaser.os.chat.infrastructure.adapter.in.rest.mensaje;

import com.renaser.os.chat.application.ports.in.mensaje.MensajeEnriquecido;
import com.renaser.os.chat.application.ports.in.mensaje.MensajeEnriquecido.RespuestaPreview;
import com.renaser.os.chat.domain.model.mensaje.EstadoDeEntrega;
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
 * {@code ConversationSummary.lastMessage}).
 *
 * <p>Lo mismo vale para {@code mediaUrl}, la URL de lectura firmada del adjunto: solo viene en el
 * listado de mensajes, que es la unica pantalla que muestra la foto o reproduce el audio. En el
 * resumen de conversacion alcanza con {@code type} para escribir "Foto" o "Audio" al lado del
 * chat, y firmar una URL por conversacion que nadie va a abrir seria trabajo tirado.
 *
 * <p><b>Un mensaje del programa (SISTEMA, D-199/D-204) nunca sale a nombre de la persona guardada en
 * {@code emisor_id}</b>: {@code senderId} es el UUID nulo ({@code Mensaje.ID_PUBLICO_DEL_PROGRAMA}),
 * {@code senderName} «Formación Renaser» (en el listado) y {@code senderAvatarUrl} {@code null}. Nunca
 * {@code senderId: null}: todas las versiones publicadas de la app lo validan como texto obligatorio
 * y un {@code null} les vaciaría la bandeja.
 *
 * <p>{@code status} (D-208, 2026-09-27): la marca de un mensaje PROPIO de quien mira, {@code SENT} (✓, el
 * servidor lo guardó) o {@code READ} (✓✓, lo leyeron; en un grupo o un soporte, todos los demás). En la
 * comunidad (GLOBAL) siempre {@code SENT}. {@code null} en los mensajes de otras personas y en los del
 * programa. Como {@code senderName}, solo viene resuelto en el listado ({@code GET .../messages}): en la
 * respuesta de enviar y en el último mensaje de la bandeja viaja {@code null}, y la app lo toma como ✓.
 * Es un campo nuevo: los APK publicados lo ignoran (su esquema del mensaje es {@code passthrough} y su
 * mapeador arma la burbuja campo por campo, verificado contra {@code origin/master}). */
public record MensajeResponse(String id, String conversationId, String senderId, String senderName,
                               String senderAvatarUrl, String type, String text, String mediaBucket,
                               String mediaPath, String mediaMime, Integer mediaBytes,
                               Short mediaDurationSeconds, String mediaUrl, boolean hidden, String replyToId,
                               ReplyPreviewResponse replyTo, String createdAt, String status) {

    public static MensajeResponse from(Mensaje m) {
        return new MensajeResponse(m.id().toString(), m.conversacionId().toString(), m.remitentePublico().toString(),
                null, null, toWireTipo(m.tipo()), m.texto(), m.mediaBucket(), m.mediaRuta(), m.mediaMime(), m.mediaBytes(),
                m.mediaDuracionS(), null, m.oculto(),
                m.respuestaAId() != null ? m.respuestaAId().toString() : null, null, m.creadoEn().toString(), null);
    }

    public static MensajeResponse from(MensajeEnriquecido enriquecido) {
        Mensaje m = enriquecido.mensaje();
        return new MensajeResponse(m.id().toString(), m.conversacionId().toString(), m.remitentePublico().toString(),
                enriquecido.nombreEmisor(), enriquecido.avatarEmisor(), toWireTipo(m.tipo()), m.texto(),
                m.mediaBucket(), m.mediaRuta(), m.mediaMime(), m.mediaBytes(), m.mediaDuracionS(),
                enriquecido.mediaUrl(), m.oculto(), m.respuestaAId() != null ? m.respuestaAId().toString() : null,
                ReplyPreviewResponse.from(enriquecido.respuestaPreview()), m.creadoEn().toString(),
                toWireEstado(enriquecido.estadoDeEntrega()));
    }

    /** La traducción de D-36 para la marca de un mensaje propio (D-208); {@code null} si no lleva. */
    static String toWireEstado(EstadoDeEntrega estado) {
        if (estado == null) {
            return null;
        }
        return switch (estado) {
            case ENVIADO -> "SENT";
            case LEIDO -> "READ";
        };
    }

    /**
     * La traducción de D-36 para el tipo de un mensaje. Pública desde el 2026-09-27 (E-333): el aviso
     * en vivo ({@code MensajeFanoutPayload}) manda el mismo valor que el REST, sacado de acá.
     */
    public static String toWireTipo(TipoMensaje tipo) {
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
