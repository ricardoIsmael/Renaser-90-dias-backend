package com.renaser.os.chat.infrastructure.adapter.out.redis;

import com.renaser.os.chat.domain.model.mensaje.Mensaje;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload liviano para el empuje en vivo por Redis Pub/Sub — no es el contrato REST
 * completo ({@code MensajeResponse}), a proposito: el cliente que ya esta mirando la
 * conversacion solo necesita saber "llego un mensaje nuevo" para refrescar; el detalle
 * completo (con paginacion keyset) sigue viniendo de {@code GET .../messages}.
 */
record MensajeFanoutPayload(UUID id, UUID conversationId, UUID senderId, String type, String text,
                             Instant createdAt) {

    static MensajeFanoutPayload from(Mensaje mensaje) {
        return new MensajeFanoutPayload(mensaje.id().value(), mensaje.conversacionId().value(),
                mensaje.emisorId().value(), mensaje.tipo().name(), mensaje.texto(), mensaje.creadoEn());
    }
}
