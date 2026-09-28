package com.renaser.os.onboarding.api;

import com.renaser.os.shared.domain.UserId;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Un aviso de la Caja Renaser (D-219). Se publica en la misma transacción que el paso que lo causa, así que
 * el outbox de Modulith lo reentrega si un oyente falla.
 *
 * @param eventoId   determinístico por (aprendiz, envío, aviso): es el {@code origen_evento_id} de la
 *                   notificación, y una reentrega no duplica la fila ni el push (C-7)
 * @param envio      el número de envío (1, o el del reenvío)
 * @param medio      solo en {@link AvisoDeCaja#EN_CAMINO}
 * @param courier    idem, puede faltar
 * @param codigo     idem
 * @param rastreoUrl idem: la página de rastreo de Olva o Shalom, si es por ellos
 * @param fotoRuta   idem: la foto de la caja armada en el almacenamiento, para mandarla al chat
 */
public record AvisoDeCajaEvent(UUID eventoId, UserId aprendizId, int envio, AvisoDeCaja aviso, String medio,
                               String courier, String codigo, String rastreoUrl, String fotoRuta) {

    public AvisoDeCajaEvent {
        Objects.requireNonNull(eventoId, "eventoId");
        Objects.requireNonNull(aprendizId, "aprendizId");
        Objects.requireNonNull(aviso, "aviso");
    }

    /** Un aviso sin datos del envío. */
    public static AvisoDeCajaEvent de(UserId aprendizId, int envio, AvisoDeCaja aviso) {
        return new AvisoDeCajaEvent(idDe(aprendizId, envio, aviso), aprendizId, envio, aviso, null, null, null, null,
                null);
    }

    /** El id determinístico de ese aviso. */
    public static UUID idDe(UserId aprendizId, int envio, AvisoDeCaja aviso) {
        String clave = "caja:" + aprendizId + ":" + envio + ":" + aviso.claveDeduplicacion();
        return UUID.nameUUIDFromBytes(clave.getBytes(StandardCharsets.UTF_8));
    }
}
