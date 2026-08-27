package com.renaser.os.shared.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.web.http.HeaderHttpSessionIdResolver;
import org.springframework.session.web.http.HttpSessionIdResolver;
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

    /**
     * El mismo bean que usa el filtro de lectura (via {@code .securityContext(...)} abajo) y que
     * {@link com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion.AutenticacionController}
     * usa para escribir el contexto tras un login exitoso. Tienen que ser el MISMO bean: si cada
     * lado tuviera su propia instancia, el patron de guardado seguiria siendo compatible (los dos
     * son {@code HttpSessionSecurityContextRepository}, sin estado propio mas alla de la key del
     * atributo de sesion), pero declararlo una vez es lo que documenta la relacion y evita que
     * alguien cambie uno sin el otro.
     */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Sesion por HEADER en vez de cookie (2026-08-27) — la app movil (Expo/React Native) no
     * maneja cookies como un browser (sin `credentials: 'include'` de por si, y el manejo de
     * cookies de `fetch` en RN es poco confiable entre iOS/Android). Declarar este bean
     * reemplaza el {@code CookieHttpSessionIdResolver} por defecto de Spring Session en TODO
     * el filtro de sesion — {@link SesionWebAdapter} no cambia nada: sigue llamando
     * {@code securityContextRepository.saveContext(...)}, es Spring Session quien decide
     * ahora escribir el id en el header {@code X-Auth-Token} de la respuesta en vez de un
     * `Set-Cookie`. El cliente lo guarda como si fuera un token (mismo nivel de sensibilidad,
     * mismo storage seguro) y lo reenvia en cada request con ese mismo header — no es JWT, no
     * cambia D-49 (sigue siendo un id de sesion opaco contra Redis).
     */
    @Bean
    HttpSessionIdResolver httpSessionIdResolver() {
        return HeaderHttpSessionIdResolver.xAuthToken();
    }

    /**
     * Sin {@code sessionCreationPolicy}: la sesion la administra Spring Session sobre Redis
     * (spring-session-data-redis, docs/MODULO_AUTH.md §4), no la {@code HttpSession} generica
     * del contenedor. Poner {@code STATELESS} aca apagaria justo lo que se quiere usar.
     *
     * <p>Todavia sin {@code .authorizeHttpRequests(anyRequest().authenticated())}: eso es la
     * fase 4 (migrar los 162 usos de {@code X-Actor-Id} en 54 controllers). Activarlo ahora
     * dejaria a toda la API existente respondiendo 401 de golpe.
     */
    @Bean
    SecurityFilterChain apiFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository)
            throws Exception {
        http.securityMatcher("/api/v1/**")
                .cors(Customizer.withDefaults())
                // Sigue deshabilitado: la sesion todavia no es el mecanismo de auth EXIGIDO en
                // ningun endpoint (X-Actor-Id sigue siendo lo que se valida, hasta la fase 4). El
                // esquema de CSRF para cuando la cookie sea obligatoria queda pendiente y ya
                // documentado (docs/MODULO_AUTH.md §5.2, D-31) — no se activa a medias.
                .csrf(csrf -> csrf.disable())
                .securityContext(ctx -> ctx.securityContextRepository(securityContextRepository))
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
     * a tocar esta clase de nuevo. `X-Auth-Token` es el id de sesion por header
     * ({@link #httpSessionIdResolver()}): va en {@code allowedHeaders} porque el build web
     * lo MANDA en cada request, y en {@code exposedHeaders} porque despues de {@code /login}
     * el JS del navegador necesita LEER ese header de la respuesta para poder guardarlo — sin
     * exponerlo, `fetch` desde un origen cruzado lo esconde aunque el header viaje igual.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origenesPermitidos);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Actor-Id", "X-Auth-Token"));
        config.setExposedHeaders(List.of("X-Auth-Token"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", config);
        return source;
    }
}
