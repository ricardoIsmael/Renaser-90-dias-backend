package com.renaser.os.chat.infrastructure.adapter.in.websocket;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import org.springframework.lang.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Traduce un destino STOMP a la conversacion que nombra, o a nada.
 *
 * <p>El id sale de una porcion del destino que <b>elige el cliente</b>, asi que puede no ser un
 * UUID y puede no ser siquiera un destino de conversacion. Devolver vacio en vez de dejar salir el
 * {@code IllegalArgumentException} crudo de {@code UUID.fromString} hace que cada canal decida que
 * significa eso: el de entrada rechaza la suscripcion, el de salida descarta la entrega. En los dos
 * el rechazo es deliberado y no un efecto colateral del parseo.
 *
 * <p><b>Por que esta aca y no copiado en cada interceptor.</b> Los dos canales tienen que estar de
 * acuerdo en que conversacion nombra un destino: si el de entrada autorizara una y el de salida
 * leyera otra, la comprobacion de la entrega miraria la conversacion equivocada — que es
 * exactamente la forma de agujero que este modulo ya se hizo una vez con la regla de acceso
 * escrita cuatro veces.
 */
final class DestinoDeConversacion {

    static final String PREFIJO = "/topic/conversaciones/";

    private DestinoDeConversacion() {
    }

    /** {@code true} si el destino pretende ser una conversacion, aunque el id no sirva. */
    static boolean loEs(@Nullable String destino) {
        return destino != null && destino.startsWith(PREFIJO);
    }

    static Optional<ConversacionId> de(@Nullable String destino) {
        if (!loEs(destino)) {
            return Optional.empty();
        }
        try {
            return Optional.of(ConversacionId.of(UUID.fromString(destino.substring(PREFIJO.length()))));
        } catch (IllegalArgumentException noEsUnUuid) {
            return Optional.empty();
        }
    }
}
