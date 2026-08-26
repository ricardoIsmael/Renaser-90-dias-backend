package com.renaser.os.users.infrastructure.adapter.out.oauth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Separado de {@link GoogleIdentidadAdapter} por el mismo motivo que {@code AppleJwtDecoderConfig}:
 * poder inyectar un {@link JwtDecoder} de prueba sin llamar de verdad al JWKS de Google en cada
 * test unitario. {@code NimbusJwtDecoder.withJwkSetUri} valida firma y vigencia (exp/nbf);
 * `iss`/`aud` se verifican a mano en el adaptador, igual que en Apple (docs/MODULO_AUTH.md §6.1
 * paso 5).
 */
@Configuration
class GoogleJwtDecoderConfig {

    private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";

    /** Nombre de bean = nombre del metodo: es lo que {@code @Qualifier("googleJwtDecoder")} matchea. */
    @Bean
    JwtDecoder googleJwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build();
    }
}
