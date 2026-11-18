package com.renaser.os.leadership.api;

import java.time.Instant;
import java.util.UUID;

/**
 * «Tu líder te dejó una observación»: el líder de mentores guardó un reconocimiento, una sugerencia o una alerta sobre un mentor, y ese mentor tiene que
 * enterarse en su teléfono (spec 056, FR-025). Sale una vez por observación, en la misma transacción que la guarda: si esta se deshace, el aviso también.
 *
 * <p>Evento y no llamada directa a {@code notifications}, por los mismos dos motivos que {@link RevisionDelLiderEvent}: la dirección de las dependencias y
 * que un reintento del outbox no repita el aviso ({@code claveDeduplicacion}).
 *
 * <p><b>No lleva el texto de la observación</b> a propósito: el aviso sale por push y puede verse con la pantalla bloqueada, y una alerta del líder no
 * debe leerse por encima del hombro del mentor. El texto vive en el chat del líder con el mentor, que es adonde lleva el aviso.
 *
 * @param claveDeduplicacion {@code UUID.nameUUIDFromBytes("observacion-del-lider:" + observacionId)}: una por observación, para que la redelivery del outbox
 *                           no cree otro aviso ni otro push
 * @param tipo               {@code RECONOCIMIENTO}, {@code SUGERENCIA} o {@code ALERTA}
 * @param conversacionId     el chat de dos del líder con el mentor donde quedó el texto; {@code null} si el mensaje ya lo había mandado una app vieja y
 *                           el servidor no conoce la conversación
 */
public record ObservacionDelLiderEvent(UUID claveDeduplicacion, UUID observacionId, UUID mentorId, UUID autorId, String tipo, UUID conversacionId,
                                       Instant generadoEn) {

    /** Ruta profunda al chat de dos donde está el texto; {@code null} si no se conoce la conversación. El cliente revalida permisos al abrirla. */
    public String rutaApp() {
        return conversacionId == null ? null : "/chat/" + conversacionId;
    }
}
