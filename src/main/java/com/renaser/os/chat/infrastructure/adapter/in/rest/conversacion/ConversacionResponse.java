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
 * teléfono baje la nueva. Sin foto propia, {@code null} como antes. La comunidad y los 1 a 1, nunca.
 *
 * <p>{@code supportTraineeId} (D-244, 2026-10-02): solo en un SOPORTE, el aprendiz de ese chat. Quien atiende
 * lo usa para ver si esa persona tiene un pedido de emergencia abierto y abrir «Cambiar día del programa». Ya
 * viajaba escondido en la clave del soporte; ahora va con nombre. {@code null} en lo demás. */
public record ConversacionResponse(String id, String type, String celulaId, String nombre, String createdAt,
                                   String photoPath, String supportTraineeId) {

    /** Sin saber la foto propia de un grupo ni su nombre derivado (la respuesta de abrir un 1 a 1). */
    public static ConversacionResponse from(Conversacion c) {
        return from(c, null, c.nombre());
    }

    /**
     * @param fotoDelGrupoCambiadaEn si es un grupo con foto propia, cuándo cambió (D-212); si no, {@code null}
     * @param nombre                 el que se muestra (D-221): en un grupo y en un soporte se deriva al leer
     *                               y NO es la columna {@code nombre}; en un 1 a 1, {@code null}. Un
     *                               {@code nombre} lleno en un grupo es nuevo: los APK publicados siguen
     *                               nombrándolo con el grupo de {@code /me/cells}, la app nueva usa este.
     */
    public static ConversacionResponse from(Conversacion c, Instant fotoDelGrupoCambiadaEn, String nombre) {
        return new ConversacionResponse(c.id().toString(), toWireTipo(c.tipo()),
                c.celulaId() != null ? c.celulaId().toString() : null, nombre, c.creadoEn().toString(),
                rutaDeLaFoto(c, fotoDelGrupoCambiadaEn),
                c.aprendizDelSoporte().map(aprendiz -> aprendiz.value().toString()).orElse(null));
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
