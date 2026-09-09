package com.renaser.os.shared.infrastructure.session;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSessionConfigTest {

    private RedisSerializer<Object> serializador() {
        RedisSessionConfig config = new RedisSessionConfig();
        config.setBeanClassLoader(getClass().getClassLoader());
        return config.springSessionDefaultRedisSerializer();
    }

    @Test
    void serializaYReconstruyeElSecurityContextComoJson() {
        RedisSerializer<Object> serializer = serializador();

        String actorId = UUID.randomUUID().toString();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(actorId, null, List.of()));

        byte[] json = serializer.serialize(context);
        Object restored = serializer.deserialize(json);

        String serialized = new String(json, StandardCharsets.UTF_8);
        assertThat(serialized).startsWith("{").contains(actorId).doesNotContain("¬í");
        assertThat(restored).isInstanceOf(SecurityContext.class);
        assertThat(((SecurityContext) restored).getAuthentication().getName()).isEqualTo(actorId);
    }

    /**
     * La metadata que Spring Session escribe EN EL MISMO hash que el SecurityContext.
     *
     * Este es el caso que el test de arriba no cubria y que fallaba en ejecucion: el
     * `PolymorphicTypeValidator` de Spring Security solo admite clases de Security, y
     * `lastAccessedTime` es un `java.lang.Long`. El sintoma no era un test rojo sino un WARN
     * del oyente de expiracion, que dejaba de limpiar las sesiones vencidas del indice:
     *
     *   Could not resolve type id 'java.lang.Long' as a subtype of `java.lang.Object`
     *     (through reference chain: java.util.HashMap["lastAccessedTime"])
     */
    @Test
    void reconstruyeLaMetadataDeLaSesionQueGuardaSpringSession() {
        RedisSerializer<Object> serializer = serializador();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("creationTime", 1_757_000_000_000L);
        metadata.put("lastAccessedTime", 1_757_000_123_456L);
        metadata.put("maxInactiveInterval", 1800);
        metadata.put("sessionAttr:algo", "un valor");

        Object restaurada = serializer.deserialize(serializer.serialize(metadata));

        assertThat(restaurada).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> leida = (Map<String, Object>) restaurada;
        assertThat(leida.get("lastAccessedTime")).isEqualTo(1_757_000_123_456L);
        assertThat(leida.get("creationTime")).isEqualTo(1_757_000_000_000L);
        assertThat(leida.get("maxInactiveInterval")).isEqualTo(1800);
        assertThat(leida.get("sessionAttr:algo")).isEqualTo("un valor");
    }
}
