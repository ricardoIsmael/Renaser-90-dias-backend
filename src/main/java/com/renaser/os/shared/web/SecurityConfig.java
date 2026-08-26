package com.renaser.os.shared.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    /**
     * Origenes permitidos para el build web de Expo. Por configuracion, NUNCA `*` hardcodeado:
     * con `allowCredentials(true)` el navegador rechaza el comodin, y ademas la lista real
     * cambia por entorno (local vs desplegado). Ver `renaser.web.cors.origenes` en application.yaml.
     */
    private final List<String> origenesPermitidos;

    SecurityConfig(@Value("${renaser.web.cors.origenes}") List<String> origenesPermitidos) {
        this.origenesPermitidos = origenesPermitidos;
    }

    /**
     * BCrypt por defecto, con el prefijo del algoritmo guardado en el hash ({@code {bcrypt}$2a$...}).
     * Se declara una sola vez acá y se inyecta donde haga falta: no se escribe logica de hasheo
     * ni de comparacion en ningun servicio. El delegating (y no BCrypt pelado) es lo que permite
     * recodificar a un algoritmo mas fuerte en el siguiente login, sin pedir cambio de contrasena.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/v1/**")
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Sin esto, el build web de Expo (un navegador) rechaza TODA llamada cross-origin a
     * `/api/v1/**` antes de que la ruta, el metodo o el cuerpo importen — no afectaba a la
     * app nativa, pero bloqueaba el 100% del target web.
     *
     * <p>`X-Actor-Id` viaja en la lista de headers permitidos porque es, por ahora, el
     * mecanismo de identidad (temporal, ver nota de los controllers); `Authorization` ya
     * queda habilitado para cuando el JWT real lo reemplace, asi que ese cambio no obliga
     * a tocar esta clase de nuevo.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origenesPermitidos);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Actor-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", config);
        return source;
    }
}
