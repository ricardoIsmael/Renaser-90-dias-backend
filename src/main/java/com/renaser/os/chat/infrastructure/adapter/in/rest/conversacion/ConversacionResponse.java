package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;

/** {@code type} en ingles (CELL/DIRECT/GLOBAL/SUPPORT) — la app publicada nunca ve
 * `tipo_conversacion` en espanol; la traduccion (D-36) vive solo aca.
 *
 * <p>{@code photoPath} (D-205, 2026-09-27): solo en un SOPORTE, la ruta de su foto (la tarjeta con el
 * primer nombre del aprendiz), relativa a la API y pedida con la sesión; {@code null} en lo demás, que
 * usa la tarjeta sin nombre que la app ya trae. Es un campo nuevo: las versiones publicadas de la app
 * lo ignoran (sus esquemas de conversación son {@code passthrough}). */
public record ConversacionResponse(String id, String type, String celulaId, String nombre, String createdAt,
                                   String photoPath) {

    public static ConversacionResponse from(Conversacion c) {
        return new ConversacionResponse(c.id().toString(), toWireTipo(c.tipo()),
                c.celulaId() != null ? c.celulaId().toString() : null, c.nombre(), c.creadoEn().toString(),
                rutaDeLaFoto(c));
    }

    static String toWireTipo(TipoConversacion tipo) {
        return switch (tipo) {
            case CELULA -> "CELL";
            case DIRECTA -> "DIRECT";
            case GLOBAL -> "GLOBAL";
            case SOPORTE -> "SUPPORT";
        };
    }

    private static String rutaDeLaFoto(Conversacion c) {
        return c.tipo() == TipoConversacion.SOPORTE
                ? ConversacionSoporteController.RUTA_DE_LA_FOTO.replace("{id}", c.id().toString())
                : null;
    }
}
