package com.renaser.os.chat.infrastructure.adapter.out.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.renaser.os.chat.application.ports.out.mensaje.PublicarMensajeFanoutPort;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Adaptador del {@code FanoutPort} (PLAN_DE_MODULOS.md linea 133) sobre Redis Pub/Sub —
 * CLAUDE.MD §5.2.1: el mensaje ya esta commiteado en Postgres antes de que este metodo se
 * llame (ver {@code MensajeService.publicarDespuesDelCommit}); esto es solo el empujon en
 * vivo a quien ya esta mirando la pantalla. Fire-and-forget: si Redis esta caido, el envio
 * del mensaje YA tuvo exito (esta en Postgres) — se loguea y se sigue, nunca se relanza.
 *
 * <p>El {@link ObjectMapper} es propio de esta clase (no inyectado): Spring Boot 4.1 autoconfigura
 * el Jackson 3 ({@code tools.jackson.databind.ObjectMapper}), no el clasico {@code com.fasterxml}
 * (Jackson 2) que usa esta clase — no hay bean Spring de ese tipo para inyectar (E-33, ver
 * {@code docs/BITACORA_ERRORES.md}). Es una necesidad interna acotada (serializar un payload
 * liviano), no justifica arrastrar el ObjectMapper de toda la app.
 */
@Component
class RedisChatPublisher implements PublicarMensajeFanoutPort {

    private static final Logger log = LoggerFactory.getLogger(RedisChatPublisher.class);
    private static final String CANAL_PREFIJO = "chat:conversacion:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    RedisChatPublisher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void publicar(Mensaje mensaje) {
        try {
            String payload = objectMapper.writeValueAsString(MensajeFanoutPayload.from(mensaje));
            redisTemplate.convertAndSend(CANAL_PREFIJO + mensaje.conversacionId().value(), payload);
        } catch (JsonProcessingException e) {
            log.warn("No se pudo serializar el mensaje {} para el fanout de Redis", mensaje.id(), e);
        } catch (RuntimeException e) {
            log.warn("No se pudo publicar el mensaje {} en Redis (el mensaje ya esta guardado en Postgres)",
                    mensaje.id(), e);
        }
    }
}
