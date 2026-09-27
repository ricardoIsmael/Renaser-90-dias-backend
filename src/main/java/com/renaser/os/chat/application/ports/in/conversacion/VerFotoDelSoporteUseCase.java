package com.renaser.os.chat.application.ports.in.conversacion;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;

/**
 * La foto del chat de SOPORTE de una persona (D-205, decisión del dueño del 2026-09-27): su tarjeta de
 * Canva con su primer nombre, la misma que la bienvenida le manda al nacer el chat. Los grupos y la
 * comunidad no tienen foto propia: la app usa la tarjeta sin nombre, que ya trae.
 *
 * <p>La ve quien puede ver la conversación (el aprendiz y el staff del soporte), con la misma regla
 * que el resto del chat ({@link AutorizarAccesoAConversacionUseCase}).
 */
public interface VerFotoDelSoporteUseCase {

    /**
     * @throws java.util.NoSuchElementException si la conversación no existe o no es un soporte (404)
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si quien pide no puede verla (403)
     */
    FotoDelSoporte foto(UserId actorId, ConversacionId conversacionId);

    /**
     * @param jpeg   la tarjeta; es compartida entre pedidos, así que nadie la modifica
     * @param huella cambia si y solo si cambia la imagen (el ETag)
     */
    record FotoDelSoporte(byte[] jpeg, String huella) {
    }
}
