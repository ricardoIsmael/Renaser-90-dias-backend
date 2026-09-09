package com.renaser.os.shared.infrastructure.session;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSessionConfigTest {

    @Test
    void serializaYReconstruyeElSecurityContextComoJson() {
        RedisSessionConfig config = new RedisSessionConfig();
        config.setBeanClassLoader(getClass().getClassLoader());
        RedisSerializer<Object> serializer = config.springSessionDefaultRedisSerializer();

        String actorId = UUID.randomUUID().toString();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(actorId, null, List.of()));

        byte[] json = serializer.serialize(context);
        Object restored = serializer.deserialize(json);

        String serialized = new String(json, StandardCharsets.UTF_8);
        assertThat(serialized).startsWith("{").contains(actorId).doesNotContain("\u00ac\u00ed");
        assertThat(restored).isInstanceOf(SecurityContext.class);
        assertThat(((SecurityContext) restored).getAuthentication().getName()).isEqualTo(actorId);
    }
}
