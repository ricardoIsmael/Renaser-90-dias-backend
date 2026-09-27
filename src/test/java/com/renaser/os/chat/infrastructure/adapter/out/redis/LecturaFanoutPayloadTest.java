package com.renaser.os.chat.infrastructure.adapter.out.redis;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * El aviso en vivo de la doble marca (D-208) tal como sale al canal: el contrato que lee la app
 * ({@code eventosDelChat.ts}, evento {@code READ}). Si alguien cambia el record, esta prueba y la de la
 * app son las que lo delatan.
 */
class LecturaFanoutPayloadTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RedisChatPublisher publicador = new RedisChatPublisher(redis);

    @Test
    @DisplayName("sale por el canal de la conversación como {\"event\":\"READ\",\"readUpTo\":…} en ISO-8601 con microsegundos")
    void elCuerpoDelAviso() {
        ConversacionId conversacion = ConversacionId.of(UUID.randomUUID());

        publicador.publicarLectura(conversacion, Instant.parse("2026-09-27T17:00:26.869554Z"));

        verify(redis).convertAndSend("chat:conversacion:" + conversacion.value(),
                "{\"event\":\"READ\",\"readUpTo\":\"2026-09-27T17:00:26.869554Z\"}");
    }

    @Test
    @DisplayName("fire-and-forget: si Redis no responde, no falla hacia arriba")
    void siRedisFallaNoLanza() {
        doThrow(new IllegalStateException("redis caído")).when(redis).convertAndSend(anyString(), anyString());

        publicador.publicarLectura(ConversacionId.of(UUID.randomUUID()), Instant.parse("2026-09-27T17:00:26Z"));
    }
}
