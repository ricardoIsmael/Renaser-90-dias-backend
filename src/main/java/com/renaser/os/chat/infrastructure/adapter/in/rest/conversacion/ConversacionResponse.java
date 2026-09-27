package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;

import java.time.Instant;

/** {@code type} en ingles (CELL/DIRECT/GLOBAL/SUPPORT) — la app publicada nunca ve
 * `tipo_conversacion` en espanol; la traduccion (D-36) vive solo aca.
 *
 * <p>{@code photoPath} (D-205, 2026-09-27): solo en un SOPORTE, la ruta de su foto (la tarjeta con el
 * primer nombre del aprendiz), relativa a la API y pedida con la sesión; {@code null} en lo demás, que
 * usa la tarjeta sin nombre que la app ya trae. Es un campo nuevo: las versiones publicadas de la app
 * lo ignoran (sus esquemas de conversación son {@code passthrough}).
 *
 * <p><b>Corregido 2026-09-27 (D-212).</b> Un GRUPO con foto propia también la trae:
 * {@code /api/v1/chat/conversations/{id}/foto?v=<milisegundos>}, con los de cuándo cambió para que el
 * teléfono baje la nueva. Sin foto propia, {@code null} como antes. La comunidad y los 1 a 1, nunca. */
public record ConversacionResponse(String id, String type, String celulaId, String nombre, String createdAt,
                                   String photoPath) {

    /** Sin saber la foto propia de un grupo (la respuesta de abrir un 1 a 1). */
    public static ConversacionResponse from(Conversacion c) {
        return from(c, null);
    }

    /** @param fotoDelGrupoCambiadaEn si es un grupo con foto propia, cuándo cambió (D-212); si no, {@code null} */
    public static ConversacionResponse from(Conversacion c, Instant fotoDelGrupoCambiadaEn) {
        return new ConversacionResponse(c.id().toString(), toWireTipo(c.tipo()),
                c.celulaId() != null ? c.celulaId().toString() : null, c.nombre(), c.creadoEn().toString(),
                rutaDeLaFoto(c, fotoDelGrupoCambiadaEn));
    }

    static String toWireTipo(TipoConversacion tipo) {
        return switch (tipo) {
            case CELULA -> "CELL";
            case DIRECTA -> "DIRECT";
            case GLOBAL -> "GLOBAL";
            case SOPORTE -> "SUPPORT";
        };
    }

    private static String rutaDeLaFoto(Conversacion c, Instant fotoDelGrupoCambiadaEn) {
        return switch (c.tipo()) {
            case SOPORTE -> FotosDelChatController.rutaDeLaFotoDelSoporte(c.id());
            case CELULA -> fotoDelGrupoCambiadaEn != null
                    ? FotosDelChatController.rutaDeLaFotoDelGrupo(c.id(), fotoDelGrupoCambiadaEn) : null;
            default -> null;
        };
    }
}
