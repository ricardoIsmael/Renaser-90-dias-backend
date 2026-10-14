package com.renaser.os.plataforma;

import com.fasterxml.jackson.databind.JsonNode;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.plataforma.application.services.CopiaDeEstados;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El escenario central de la spec 002 (US1; contrato §9), de punta a punta: Soporte TI apaga una funcionalidad para un
 * rol desde la consola y el servidor la corta para ese rol —con el aviso como mensaje, que un APK viejo muestra como
 * cualquier error— y no para los demás; al prenderla vuelve. Cubre las dos formas de cortar: el interceptor por el
 * {@code @Funcionalidad} de la ruta ({@code /me/cells}) y el servicio por el tipo de la conversación (mensajes del chat
 * global), y que con lo apagado no se escribe nada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ApagarUnaFuncionalidadIT {

    private static final String AVISO = "El chat de grupo vuelve a las 18:00.";

    @LocalServerPort
    private int puerto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private CopiaDeEstados copia;

    private PedidosDePrueba pedidos;
    private String soporte;
    private String consola;
    private final List<UUID> participaciones = new ArrayList<>();
    private UUID global;
    private boolean globalCreadaAca;
    private final Instant inicio = Instant.now();

    @BeforeEach
    void armar() throws Exception {
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
        soporte = pedidos.sesionDe(pedidos.usuario("SOPORTE_TI"));
        consola = pedidos.desbloquear(soporte);
    }

    @AfterEach
    void limpiar() throws InterruptedException {
        pedidos.esperarElOutbox(inicio);
        jdbc.update("DELETE FROM renaser.funcionalidades_estado");
        copia.recargar();
        participaciones.forEach(usuario -> {
            jdbc.update("DELETE FROM renaser.mensajes WHERE emisor_id = ?", usuario);
            jdbc.update("DELETE FROM renaser.participantes_conversacion WHERE usuario_id = ?", usuario);
        });
        if (globalCreadaAca) {
            jdbc.update("DELETE FROM renaser.conversaciones WHERE id = ?", global);
        }
        pedidos.borrarCuentas();
    }

    @Test
    @DisplayName("chat.grupos apagado para APRENDIZ: el aprendiz recibe 503 con el aviso, el mentor 200; al prenderlo, "
            + "el aprendiz vuelve a 200 en menos de 5 s")
    void apagarYPrenderParaUnRol() throws Exception {
        String aprendiz = pedidos.sesionDe(pedidos.usuario("APRENDIZ"));
        String mentor = pedidos.sesionDe(pedidos.usuario("MENTOR"));
        assertThat(pedidos.enviar("GET", "/api/v1/me/cells", aprendiz, null).statusCode()).isEqualTo(200);

        long version = cambiar("chat.grupos", true, Map.of("TRAINEE", false), AVISO, 0, null);

        HttpResponse<String> delAprendiz = pedidos.enviar("GET", "/api/v1/me/cells", aprendiz, null);
        assertThat(delAprendiz.statusCode()).isEqualTo(503);
        JsonNode error = pedidos.json(delAprendiz);
        assertThat(error.get("message").asText()).as("el APK 1.5.0 muestra message").isEqualTo(AVISO);
        assertThat(error.get("codigo").asText()).isEqualTo("FUNCIONALIDAD_APAGADA");
        assertThat(error.get("funcionalidad").asText()).isEqualTo("chat.grupos");
        assertThat(pedidos.enviar("GET", "/api/v1/me/cells", mentor, null).statusCode()).isEqualTo(200);

        cambiar("chat.grupos", true, Map.of("TRAINEE", true), null, version, null);

        assertThat(esperarEstado("/api/v1/me/cells", aprendiz, 200, 5_000)).as("vuelve en ≤ 5 s").isTrue();
    }

    @Test
    @DisplayName("chat.global apagado para APRENDIZ: el servicio corta los mensajes de la conversación global para el "
            + "aprendiz (también al escribir: no se guarda nada) y no para el mentor")
    void elServicioCortaPorElTipoDeConversacion() throws Exception {
        UUID aprendizId = pedidos.usuario("APRENDIZ");
        UUID mentorId = pedidos.usuario("MENTOR");
        participarDeLaGlobal(aprendizId);
        participarDeLaGlobal(mentorId);
        String aprendiz = pedidos.sesionDe(aprendizId);
        String mentor = pedidos.sesionDe(mentorId);
        String mensajes = "/api/v1/chat/conversations/" + global + "/messages";
        assertThat(pedidos.enviar("GET", mensajes, aprendiz, null).statusCode()).isEqualTo(200);

        cambiar("chat.global", true, Map.of("TRAINEE", false), "La comunidad vuelve pronto.", 0, null);

        HttpResponse<String> leer = pedidos.enviar("GET", mensajes, aprendiz, null);
        HttpResponse<String> escribir = pedidos.enviar("POST", mensajes, aprendiz,
                Map.of("type", "TEXT", "text", "hola"));
        assertThat(leer.statusCode()).isEqualTo(503);
        assertThat(pedidos.json(leer).get("funcionalidad").asText()).isEqualTo("chat.global");
        assertThat(escribir.statusCode()).isEqualTo(503);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.mensajes WHERE emisor_id = ?", Integer.class,
                aprendizId)).as("con lo apagado no se escribe nada").isZero();
        assertThat(pedidos.enviar("GET", mensajes, mentor, null).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("apagar la madre «chat» para todos (crítico) corta también el chat global del mentor, con el aviso de "
            + "la madre")
    void laMadreApagaALasHijas() throws Exception {
        UUID mentorId = pedidos.usuario("MENTOR");
        participarDeLaGlobal(mentorId);
        String mentor = pedidos.sesionDe(mentorId);

        cambiar("chat", false, Map.of(), "Chat en mantenimiento.", 0, pedidos.reautenticar(soporte, consola));

        HttpResponse<String> respuesta = pedidos.enviar("GET", "/api/v1/chat/conversations/" + global + "/messages",
                mentor, null);
        assertThat(respuesta.statusCode()).isEqualTo(503);
        assertThat(pedidos.json(respuesta).get("message").asText()).isEqualTo("Chat en mantenimiento.");
        assertThat(pedidos.enviar("GET", "/api/v1/me/cells", mentor, null).statusCode())
                .as("chat.grupos es hija de chat").isEqualTo(503);
        assertThat(pedidos.enviar("GET", "/api/v1/wall", mentor, null).statusCode())
                .as("el muro no es del chat").isNotEqualTo(503);
    }

    // ── Semilla y pedidos ──────────────────────────────────────────────────

    private long cambiar(String clave, boolean prendida, Map<String, Boolean> porRol, String aviso, long version,
                         String reautenticacion) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("prendidaGlobal", prendida);
        cuerpo.put("porRol", porRol);
        cuerpo.put("aviso", aviso);
        cuerpo.put("motivo", "Prueba de punta a punta");
        cuerpo.put("version", version);
        Map<String, String> cabeceras = new LinkedHashMap<>();
        cabeceras.put("X-Consola-Token", consola);
        if (reautenticacion != null) {
            cabeceras.put("X-Reautenticacion", reautenticacion);
        }
        HttpResponse<String> respuesta = pedidos.enviar("PUT", "/api/v1/soporte-ti/funcionalidades/" + clave, soporte,
                cuerpo, cabeceras);
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        return pedidos.json(respuesta).get("version").asLong();
    }

    private void participarDeLaGlobal(UUID usuario) {
        if (global == null) {
            List<UUID> existente = jdbc.queryForList("SELECT id FROM renaser.conversaciones WHERE tipo = 'GLOBAL'",
                    UUID.class);
            globalCreadaAca = existente.isEmpty();
            global = globalCreadaAca ? UUID.randomUUID() : existente.getFirst();
            if (globalCreadaAca) {
                jdbc.update("INSERT INTO renaser.conversaciones (id, tipo, nombre) VALUES (?, 'GLOBAL', 'Comunidad')",
                        global);
            }
        }
        jdbc.update("INSERT INTO renaser.participantes_conversacion (conversacion_id, usuario_id) VALUES (?, ?)",
                global, usuario);
        participaciones.add(usuario);
    }

    private boolean esperarEstado(String ruta, String sesion, int estado, long milisegundos) throws Exception {
        long limite = System.currentTimeMillis() + milisegundos;
        while (System.currentTimeMillis() < limite) {
            if (pedidos.enviar("GET", ruta, sesion, null).statusCode() == estado) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }
}
