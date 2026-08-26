package com.renaser.os.users.infrastructure.adapter.out.oauth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Separado de {@link AppleIdentidadAdapter} para poder inyectar un {@link JwtDecoder} de prueba
 * en el adaptador sin hacer una llamada de red real al JWKS de Apple en cada test unitario.
 * {@code NimbusJwtDecoder.withJwkSetUri} valida firma y vigencia (exp/nbf); `iss`/`aud` se
 * verifican a mano en el adaptador porque son especificos de Apple (docs/MODULO_AUTH.md §6.1
 * paso 5).
 */
@Configuration
class AppleJwtDecoderConfig {

    private static final String JWKS_URI = "https://appleid.apple.com/auth/keys";

    @Bean
    JwtDecoder appleJwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build();
    }
}
