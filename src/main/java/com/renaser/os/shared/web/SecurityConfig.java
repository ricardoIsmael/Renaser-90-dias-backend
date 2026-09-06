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
     * <p>Todavia sin {@code anyRequest().authenticated()} para el resto de la API: eso es la
     * fase 4 (migrar los 162 usos de {@code X-Actor-Id} en 54 controllers). Activarlo entero
     * ahora dejaria a toda la API existente respondiendo 401 de golpe.
     *
     * <p><b>Renasia es la excepcion, y es deliberada (2026-09-03).</b> Sus rutas SI exigen sesion
     * real. El motivo no es de estilo: fuera de esas rutas, cuando no hay sesion el actor se
     * resuelve del header {@code X-Actor-Id}, que lo escribe el propio cliente. Sobre un agente
     * conversacional eso significa que cualquiera puede hablarle como si fuera otra persona, leer
     * su progreso a traves de las herramientas del agente y gastarle la cuota diaria. La regla de
     * que ninguna herramienta reciba un id de usuario por parametro protege contra que lo elija el
     * modelo; no protege contra que lo falsee el cliente. Esto ultimo si.
     *
     * <p>Se hace acotado a propósito: exigir sesion en dos rutas no obliga a migrar los otros 54
     * controllers, asi que la fase 4 sigue pendiente igual y nada mas cambia de comportamiento.
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
                .authorizeHttpRequests(auth -> auth
                        // ---------------------------------------------------------------------
                        // PUBLICOS: por definicion no puede haber sesion todavia.
                        // ---------------------------------------------------------------------
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/social",
                                "/api/v1/auth/social/complete").permitAll()
                        .requestMatchers("/api/v1/auth/password/**",
                                "/api/v1/auth/email-verification/**").permitAll()
                        .requestMatchers("/api/v1/account-requests/**").permitAll()

                        // ---------------------------------------------------------------------
                        // LO QUE CONSUME LA APP MOVIL: exige sesion real (2026-09-05).
                        //
                        // Estas ~40 rutas se extrajeron del frontend, de las llamadas reales a
                        // `/api/v1/...`. Cerrarlas es lo que termina con la suplantacion entre
                        // aprendices: hasta ahora el actor salia del header `X-Actor-Id`, que lo
                        // escribe el cliente, asi que cualquiera que supiera el UUID de otro
                        // podia leer y escribir como esa persona. Los UUID no son secretos: 14
                        // DTOs de respuesta los devuelven (muro, ranking, testimonios, tickets).
                        //
                        // No hace falta tocar los controllers para que esto funcione:
                        // `ActorAutenticadoArgumentResolver` YA prefiere la sesion sobre el
                        // header. Al exigir `authenticated()`, el `SecurityContext` viene poblado
                        // con el usuario real y el header deja de leerse en estas rutas.
                        //
                        // Es `authenticated()` y NO un chequeo de rol TRAINEE a proposito: un
                        // mentor o un admin abren las mismas pantallas, y filtrar por rol aca los
                        // dejaria afuera. Que puede hacer cada rol lo sigue decidiendo
                        // `@RequiresPermission` + `PermissionEnforcementInterceptor`.
                        // ---------------------------------------------------------------------
                        .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/home", "/api/v1/users/me/**").authenticated()
                        .requestMatchers("/api/v1/habits", "/api/v1/habit-preferences/**",
                                "/api/v1/habit-tracks/**", "/api/v1/habit-unlocks/**").authenticated()
                        .requestMatchers("/api/v1/evidence", "/api/v1/evidence/**").authenticated()
                        .requestMatchers("/api/v1/classroom/**", "/api/v1/cursos/**",
                                "/api/v1/lecciones/**").authenticated()
                        .requestMatchers("/api/v1/wall/**", "/api/v1/chat/**",
                                "/api/v1/me/cell/**").authenticated()
                        .requestMatchers("/api/v1/onboarding/**", "/api/v1/rocks/**",
                                "/api/v1/spirit-audio/**").authenticated()
                        .requestMatchers("/api/v1/tickets/**", "/api/v1/ranking/**").authenticated()
                        // Renasia ya lo exigia desde 2026-09-03, por el mismo motivo.
                        .requestMatchers("/api/v1/renasia/**").authenticated()

                        // ---------------------------------------------------------------------
                        // ADMINISTRACION (2026-09-06). Estaba en permitAll mientras el backend
                        // corria solo en la maquina del dueno, donde nadie mas llegaba. Al salir
                        // a internet eso cambia de significado: los guards de servicio
                        // (requireAdminActivo) verifican el ROL del UUID que llega en
                        // X-Actor-Id, no que quien llama SEA ese usuario — y los UUID no son
                        // secretos, catorce DTOs de respuesta los devuelven. Sin esta linea,
                        // publicar el backend es publicar un panel de administracion sin
                        // contrasena.
                        // ---------------------------------------------------------------------
                        .requestMatchers("/api/v1/admin/**").authenticated()

                        // ---------------------------------------------------------------------
                        // LO QUE SE ESCAPO POR UN MATCHER EXACTO (auditoria NFR 2026-09-06).
                        //
                        // Arriba, `"/api/v1/habits"` es una coincidencia EXACTA: cubre GET y
                        // POST del catalogo, pero NO `/api/v1/habits/{id}/rename` (PUT renombrar,
                        // DELETE quitar del plan). Y `"/api/v1/users/me/**"` cubre lo propio, pero
                        // NO `POST /api/v1/users/invite` ni `PATCH /api/v1/users/{id}/role` — que
                        // son INVITAR USUARIOS y CAMBIAR ROLES. Su guard (`requireRoleManager`)
                        // verifica el rol del UUID que llega en `X-Actor-Id`, no que quien llama
                        // sea ese usuario; y los UUID no son secretos. Era el mismo agujero que se
                        // cerro esta manana en `/api/v1/admin/**`, en dos rutas mas.
                        //
                        // Ninguna ruta publica vive bajo estos prefijos (el alta es
                        // `/account-requests`, la activacion y el reset son `/auth/**`).
                        // ---------------------------------------------------------------------
                        .requestMatchers("/api/v1/habits/**", "/api/v1/users/**").authenticated()

                        // ---------------------------------------------------------------------
                        // EL RESTO DE LO QUE TOCA DATOS DE UNA PERSONA (2026-09-06).
                        //
                        // Estas dieciseis rutas quedaron fuera de la primera lista, que se armo
                        // leyendo las llamadas del frontend: varias no se usan todavia desde la
                        // app, y por eso no aparecieron. Pero "no se usa" no es "no se alcanza" —
                        // con el backend detras de CloudFront, cualquiera puede llamarlas.
                        //
                        // Dos son las que apuran el cambio: `journal/today` es el diario personal
                        // del aprendiz y `espejo-sombra` son sus informes del Espejo. Es el dato
                        // mas intimo que guarda el producto, y se alcanzaba poniendo un UUID en un
                        // header. `phase-contracts` es la firma del Pacto de Sangre: sin esto,
                        // alguien podia firmarlo en nombre de otro.
                        // ---------------------------------------------------------------------
                        .requestMatchers("/api/v1/journal/**", "/api/v1/espejo-sombra/**",
                                "/api/v1/radar/**", "/api/v1/profile/**").authenticated()
                        .requestMatchers("/api/v1/phase-contracts/**", "/api/v1/points/**",
                                "/api/v1/weekly-habit-days/**").authenticated()
                        .requestMatchers("/api/v1/notifications/**", "/api/v1/notification-preferences/**",
                                "/api/v1/push-tokens/**").authenticated()
                        .requestMatchers("/api/v1/calendar/**", "/api/v1/support-tickets/**",
                                "/api/v1/testimonios/**").authenticated()
                        .requestMatchers("/api/v1/audio-therapy/**", "/api/v1/academia/**",
                                "/api/v1/enforcer-events/**").authenticated()

                        // ---------------------------------------------------------------------
                        // EL RESTO sigue abierto: son rutas que la app movil todavia no consume.
                        // Ya NO incluyen `/api/v1/admin/**`, que se cerro arriba al salir a
                        // internet. Lo que queda son endpoints sueltos que no exponen datos de
                        // otras personas; el candidato mas visible a cerrarse despues es
                        // `/api/v1/account-requests/**`, que hoy resuelve el actor por header.
                        // ---------------------------------------------------------------------
                        .anyRequest().permitAll());
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
