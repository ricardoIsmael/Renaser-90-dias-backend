package com.renaser.os.plataforma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lo que repiten las pruebas de punta a punta de {@code plataforma}: cuentas de prueba por SQL (con contraseña si hace
 * falta), sesiones reales de Spring Session y pedidos HTTP con cabeceras, como los manda la app.
 */
public final class PedidosDePrueba {

    public static final String CONTRASENA = "Secreta123!clave";

    private final int puerto;
    private final JdbcTemplate jdbc;
    private final SessionRepository<? extends Session> sesiones;
    private final PasswordEncoder passwordEncoder;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> cuentas = new ArrayList<>();

    public PedidosDePrueba(int puerto, JdbcTemplate jdbc, SessionRepository<? extends Session> sesiones,
                           PasswordEncoder passwordEncoder) {
        this.puerto = puerto;
        this.jdbc = jdbc;
        this.sesiones = sesiones;
        this.passwordEncoder = passwordEncoder;
    }

    /** Una cuenta con ese rol (nombre de la base: APRENDIZ, SOPORTE_TI…), estado y la contraseña de prueba. */
    public UUID usuario(String rol, String estado) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado, hash_contrasena)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), CAST(? AS renaser.estado_usuario), ?)
                """, id, id + "@renaser.test", "Fixture " + rol, rol, estado, passwordEncoder.encode(CONTRASENA));
        cuentas.add(id);
        return id;
    }

    public UUID usuario(String rol) {
        return usuario(rol, "ACTIVO");
    }

    /** Borra las cuentas de la prueba (la auditoría las sigue nombrando por id: no tiene FK, a propósito). */
    public void borrarCuentas() {
        for (UUID id : cuentas) {
            jdbc.update("DELETE FROM renaser.notificaciones WHERE usuario_id = ?", id);
            jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id);
        }
        cuentas.clear();
    }

    /** Espera a que el outbox entregue lo publicado desde {@code desde} (los avisos de un cambio). */
    public void esperarElOutbox(java.time.Instant desde) throws InterruptedException {
        long limite = System.currentTimeMillis() + 20_000;
        while (pendientes(desde) > 0 && System.currentTimeMillis() < limite) {
            Thread.sleep(200);
        }
    }

    private int pendientes(java.time.Instant desde) {
        return jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE completion_date IS NULL "
                + "AND publication_date >= ?", Integer.class, java.sql.Timestamp.from(desde));
    }

    public String sesionDe(UUID usuario) {
        return guardar(sesiones, usuario);
    }

    public HttpResponse<String> enviar(String metodo, String ruta, String sesion, Object cuerpo,
                                       Map<String, String> cabeceras) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo), StandardCharsets.UTF_8))
                .header("X-Auth-Token", sesion)
                .header("User-Agent", "PruebaDePlataforma/1.0");
        if (cuerpo != null) {
            pedido.header("Content-Type", "application/json");
        }
        cabeceras.forEach(pedido::header);
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    public HttpResponse<String> enviar(String metodo, String ruta, String sesion, Object cuerpo) throws Exception {
        return enviar(metodo, ruta, sesion, cuerpo, Map.of());
    }

    public JsonNode json(HttpResponse<String> respuesta) throws Exception {
        return json.readTree(respuesta.body());
    }

    /** Desbloquea la consola de esa sesión y devuelve el token de {@code X-Consola-Token}. */
    public String desbloquear(String sesion) throws Exception {
        HttpResponse<String> respuesta = enviar("POST", "/api/v1/soporte-ti/desbloqueo", sesion,
                Map.of("contrasena", CONTRASENA, "para", "CONSOLA"));
        if (respuesta.statusCode() != 200) {
            throw new IllegalStateException("No se pudo desbloquear: " + respuesta.statusCode() + " " + respuesta.body());
        }
        return json(respuesta).get("token").asText();
    }

    /** Pide el token de un solo uso para un cambio crítico. */
    public String reautenticar(String sesion, String tokenDeConsola) throws Exception {
        HttpResponse<String> respuesta = enviar("POST", "/api/v1/soporte-ti/desbloqueo", sesion,
                Map.of("contrasena", CONTRASENA, "para", "CAMBIO_CRITICO"), Map.of("X-Consola-Token", tokenDeConsola));
        return json(respuesta).get("token").asText();
    }

    private static <S extends Session> String guardar(SessionRepository<S> repositorio, UUID usuario) {
        S sesion = repositorio.createSession();
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(usuario.toString(), null, List.of()));
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
        repositorio.save(sesion);
        return sesion.getId();
    }
}
