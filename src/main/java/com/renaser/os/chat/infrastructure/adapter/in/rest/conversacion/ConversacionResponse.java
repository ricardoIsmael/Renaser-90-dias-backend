package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;

import java.time.Instant;
import java.util.Optional;

/** {@code type} en ingles (CELL/DIRECT/GLOBAL/SUPPORT) — la app publicada nunca ve
 * `tipo_conversacion` en espanol; la traduccion (D-36) vive solo aca.
 *
 * <p>{@code photoPath} (D-205, 2026-09-27): solo en un SOPORTE, la ruta de su foto (la tarjeta con el
 * primer nombre del aprendiz), relativa a la API y pedida con la sesión; {@code null} en lo demás, que
 * usa la tarjeta sin nombre que la app ya trae. Es un campo nuevo: las versiones publicadas de la app
 * lo ignoran (sus esquemas de conversación son {@code passthrough}).
 *
 * <p><b>Spec 035.</b> La comunidad general y un grupo SIN foto propia traen la ruta de la imagen de su formación (la misma ruta y el mismo {@code ?v=}),
 * solo si la formación tiene una; sin ella, {@code null} y la app usa el fénix y la tarjeta de siempre. Un grupo con foto propia conserva la suya.
 *
 * <p>{@code ownPhoto} (spec 035): solo en un GRUPO, si la foto de {@code photoPath} es la PROPIA del grupo (D-212). Con la imagen de la formación
 * {@code photoPath} viene aunque el grupo no tenga foto propia, y quien puede cambiar la foto del grupo necesita saber cuál es (para ofrecer «Volver a la foto
 * de Renaser» solo si hay una propia). {@code null} en lo demás; un cliente anterior lo ignora.
 *
 * <p><b>Corregido 2026-09-27 (D-212).</b> Un GRUPO con foto propia también la trae:
 * {@code /api/v1/chat/conversations/{id}/foto?v=<milisegundos>}, con los de cuándo cambió para que el
 * teléfono baje la nueva. Sin foto propia, {@code null} como antes. La comunidad y los 1 a 1, nunca.
 *
 * <p>{@code supportTraineeId} (D-244, 2026-10-02): solo en un SOPORTE, el aprendiz de ese chat. Quien atiende
 * lo usa para ver si esa persona tiene un pedido de emergencia abierto y abrir «Cambiar día del programa». Ya
 * viajaba escondido en la clave del soporte; ahora va con nombre. {@code null} en lo demás.
 *
 * <p>{@code formation} (spec 024): el código de la formación de la conversación. La app de Administración rotula con él cada fila cuando lee
 * «Todas», y la del personal con varias formaciones lo usa para cambiar de formación activa al abrir un aviso. Un cliente anterior lo ignora. */
public record ConversacionResponse(String id, String type, String celulaId, String nombre, String createdAt,
                                   String photoPath, String supportTraineeId, String formation, Boolean ownPhoto) {

    /** Sin saber la foto propia de un grupo ni su nombre derivado (la respuesta de abrir un 1 a 1). */
    public static ConversacionResponse from(Conversacion c) {
        return from(c, null, null, c.nombre());
    }

    /**
     * @param fotoDelGrupoCambiadaEn si es un grupo con foto propia, cuándo cambió (D-212); si no, {@code null}
     * @param imagenDeLaFormacionDesde en la comunidad general y en un grupo sin foto propia, desde cuándo es la imagen de su formación (spec 035); si no
     *                               tiene, {@code null} y la app usa la suya
     * @param nombre                 el que se muestra (D-221): en un grupo y en un soporte se deriva al leer
     *                               y NO es la columna {@code nombre}; en un 1 a 1, {@code null}. Un
     *                               {@code nombre} lleno en un grupo es nuevo: los APK publicados siguen
     *                               nombrándolo con el grupo de {@code /me/cells}, la app nueva usa este.
     */
    public static ConversacionResponse from(Conversacion c, Instant fotoDelGrupoCambiadaEn, Instant imagenDeLaFormacionDesde,
                                           String nombre) {
        return new ConversacionResponse(c.id().toString(), toWireTipo(c.tipo()),
                c.celulaId() != null ? c.celulaId().toString() : null, nombre, c.creadoEn().toString(),
                rutaDeLaFoto(c, fotoDelGrupoCambiadaEn, imagenDeLaFormacionDesde),
                c.aprendizDelSoporte().map(aprendiz -> aprendiz.value().toString()).orElse(null),
                c.formacion().codigo(), c.tipo() == TipoConversacion.CELULA ? fotoDelGrupoCambiadaEn != null : null);
    }

    static String toWireTipo(TipoConversacion tipo) {
        return switch (tipo) {
            case CELULA -> "CELL";
            case DIRECTA -> "DIRECT";
            case GLOBAL -> "GLOBAL";
            case SOPORTE -> "SUPPORT";
        };
    }

    private static String rutaDeLaFoto(Conversacion c, Instant fotoDelGrupoCambiadaEn, Instant imagenDeLaFormacionDesde) {
        return switch (c.tipo()) {
            case SOPORTE -> FotosDelChatController.rutaDeLaFotoDelSoporte(c.id());
            case CELULA -> desdeCuando(fotoDelGrupoCambiadaEn, imagenDeLaFormacionDesde)
                    .map(desde -> FotosDelChatController.rutaDeLaFotoDelGrupo(c.id(), desde)).orElse(null);
            case GLOBAL -> imagenDeLaFormacionDesde == null ? null
                    : FotosDelChatController.rutaDeLaFotoDelGrupo(c.id(), imagenDeLaFormacionDesde);
            default -> null;
        };
    }

    /** La foto propia del grupo manda; si no tiene, la imagen de su formación (spec 035). */
    private static Optional<Instant> desdeCuando(Instant fotoDelGrupoCambiadaEn, Instant imagenDeLaFormacionDesde) {
        return Optional.ofNullable(fotoDelGrupoCambiadaEn != null ? fotoDelGrupoCambiadaEn : imagenDeLaFormacionDesde);
    }
}
