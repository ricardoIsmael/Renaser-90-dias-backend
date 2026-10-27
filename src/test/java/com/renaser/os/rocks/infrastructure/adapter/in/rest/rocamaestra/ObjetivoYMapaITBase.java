package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocamaestra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que comparten los IT de D-284 (issue #20): una aprendiz con sesion real que define su objetivo de 90 dias
 * desde Plan y despues activa su Mapa, contra Postgres, Redis y la cadena HTTP completa. Lo unico que cambia
 * entre las dos clases es {@code renaser.rocks.exigir-mapa-activo}.
 *
 * <p>El orden de llamadas es el de la app (`useMapaRenacimiento.activar`): primero los
 * {@code PUT /rocks/master/{eje}} con {@code "origen": "MAPA"}, y solo al final
 * {@code POST /mapa-renacimiento/completar}, que es lo que deja el Mapa activo. Los logs de produccion del
 * 2026-10-27 muestran exactamente ese orden: un 200 y despues los 409 de los reintentos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
abstract class ObjetivoYMapaITBase {

    static final String ROCAS = "/api/v1/rocks/master/";
    static final String COMPLETAR_MAPA = "/api/v1/mapa-renacimiento/completar";
    static final String OBJETIVO_DE_PLAN = "Facturar 30.000 USD en contratos high-ticket";
    static final String OBJETIVO_DEL_MAPA = "Facturar 45.000 USD con tres clientes recurrentes";
    private static final String CLAVE = "ClaveDelObjetivoIT-2026!";

    @LocalServerPort private int puerto;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private SessionRepository<? extends Session> sesiones;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<String> tokens = new ArrayList<>();

    @AfterEach
    void limpiar() {
        tokens.forEach(sesiones::deleteById);
        usuarios.reversed().forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    /** Una aprendiz con programa en marcha y sesion abierta por el login real. Devuelve su token. */
    String aprendizConSesion() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado, hash_contrasena)
                VALUES (?, ?, 'Objetivo IT', 'APRENDIZ', 'ACTIVO', ?)
                """, id, id + "@renaser.test", encoder.encode(CLAVE));
        usuarios.add(id);
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, programa_activado_en) "
                + "VALUES (?, 10, now())", id);
        HttpResponse<String> login = pedir("POST", "/api/v1/auth/login", null,
                Map.of("email", id + "@renaser.test", "contrasena", CLAVE));
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        String token = login.headers().firstValue("X-Auth-Token").orElseThrow();
        tokens.add(token);
        return token;
    }

    /** {@code PUT /rocks/master/TRABAJO} con meta, como la manda la app. {@code origen} {@code null} = Plan. */
    HttpResponse<String> definir(String token, String eje, String objetivo, int meta, int base, String origen)
            throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("objetivo", objetivo);
        cuerpo.put("meta", meta);
        cuerpo.put("avance", base);
        cuerpo.put("unidad", "USD");
        cuerpo.put("lineaBase", base);
        if (origen != null) cuerpo.put("origen", origen);
        return pedir("PUT", ROCAS + eje, token, cuerpo);
    }

    HttpResponse<String> completarElMapa(String token) throws Exception {
        return pedir("POST", COMPLETAR_MAPA, token, null);
    }

    JsonNode ok(HttpResponse<String> respuesta) throws Exception {
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        return json.readTree(respuesta.body());
    }

    JsonNode conflicto(HttpResponse<String> respuesta, String codigo) throws Exception {
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(409);
        JsonNode cuerpo = json.readTree(respuesta.body());
        assertThat(cuerpo.get("codigo").asText()).isEqualTo(codigo);
        return cuerpo;
    }

    private HttpResponse<String> pedir(String metodo, String ruta, String token, Object cuerpo) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta));
        if (token != null) pedido.header("X-Auth-Token", token);
        if (cuerpo == null) pedido.method(metodo, HttpRequest.BodyPublishers.noBody());
        else pedido.header("Content-Type", "application/json")
                .method(metodo, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** La roca de ese eje tal como la devuelve {@code GET /rocks/master}, o {@code null} si no hay. */
    JsonNode rocaDe(String token, String eje) throws Exception {
        for (JsonNode roca : ok(pedir("GET", "/api/v1/rocks/master", token, null))) {
            if (eje.equals(roca.get("eje").asText())) return roca;
        }
        return null;
    }
}
