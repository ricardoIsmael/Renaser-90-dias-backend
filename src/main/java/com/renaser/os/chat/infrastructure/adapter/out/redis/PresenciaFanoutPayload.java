package com.renaser.os.chat.infrastructure.adapter.out.redis;

import java.util.UUID;

/**
 * "Esta persona abrio (o cerro) su ultimo socket." Viaja por el canal de la conversacion,
 * junto a los mensajes, y se distingue de ellos por {@code event}.
 *
 * <p>Sin {@code conversationId}: el canal de Redis ya lo lleva en su nombre
 * ({@code chat:conversacion:{id}}) y el cliente lo sabe por la suscripcion en la que le
 * llego. Repetirlo seria una copia que podria contradecir a la otra.
 */
record PresenciaFanoutPayload(String event, UUID userId, boolean online) {

    static final String EVENTO = "PRESENCE";

    static PresenciaFanoutPayload de(UUID userId, boolean online) {
        return new PresenciaFanoutPayload(EVENTO, userId, online);
    }
}
