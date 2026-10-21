package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.plataforma.PedidosDePrueba;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La consola Soporte TI de punta a punta (spec 002, contrato §6 y §9): sesión real, Spring Security, los dos
 * interceptores, los servicios, Postgres con V100 y V101 y Redis para los tokens.
 *
 * <p>Cubre la autorización negativa de cada endpoint (ADMIN, ALQUIMISTA, MENTOR, APRENDIZ y una cuenta Soporte TI
 * suspendida → 403; sin desbloqueo → 423; crítico sin reautenticar → 428), el bloqueo por intentos, el bloqueo
 * optimista, la auditoría (y que la base la defiende de UPDATE y DELETE) y la lectura de la app con ETag.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ConsolaSoporteTiIT {

    private static final List<String> ROLES_SIN_CONSOLA = List.of("ADMIN", "ALQUIMISTA", "MENTOR", "APRENDIZ",
            "GUIA_APRENDIZ", "LIDER_MENTORES");

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
    private final Instant inicio = Instant.now();

    @BeforeEach
    void armar() {
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
    }

    @AfterEach
    void limpiar() throws InterruptedException {
        pedidos.esperarElOutbox(inicio);
        jdbc.update("DELETE FROM renaser.funcionalidades_estado");
        copia.recargar();
        pedidos.borrarCuentas();
    }

    @Test
    @DisplayName("ningún otro rol entra a la consola, ni ADMIN ni ALQUIMISTA: 403 en cada endpoint (FR-025)")
    void ningunOtroRolEntra() throws Exception {
        for (String rol : ROLES_SIN_CONSOLA) {
            String sesion = pedidos.sesionDe(pedidos.usuario(rol));
            for (Pedido pedido : pedidosDeLaConsola()) {
                HttpResponse<String> respuesta = pedidos.enviar(pedido.metodo(), pedido.ruta(), sesion, pedido.cuerpo());

                assertThat(respuesta.statusCode()).as("%s %s como %s", pedido.metodo(), pedido.ruta(), rol)
                        .isEqualTo(403);
            }
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.funcionalidades_estado", Integer.class)).isZero();
    }

    @Test
    @DisplayName("una cuenta Soporte TI suspendida tampoco entra: 403 aunque su sesión siga viva")
    void soporteTiSuspendidoNoEntra() throws Exception {
        String sesion = pedidos.sesionDe(pedidos.usuario("SOPORTE_TI", "SUSPENDIDO"));
        for (Pedido pedido : pedidosDeLaConsola()) {
            assertThat(pedidos.enviar(pedido.metodo(), pedido.ruta(), sesion, pedido.cuerpo()).statusCode())
                    .as("%s %s", pedido.metodo(), pedido.ruta()).isEqualTo(403);
        }
    }

    @Test
    @DisplayName("Soporte TI sin desbloquear: 423 CONSOLA_BLOQUEADA en todo menos el desbloqueo")
    void sinDesbloquearEsta423() throws Exception {
        String sesion = pedidos.sesionDe(pedidos.usuario("SOPORTE_TI"));
        for (Pedido pedido : pedidosDeLaConsola()) {
            if (pedido.ruta().endsWith("/desbloqueo")) {
                continue;
            }
            HttpResponse<String> respuesta = pedidos.enviar(pedido.metodo(), pedido.ruta(), sesion, pedido.cuerpo(),
                    Map.of("X-Consola-Token", "inventado"));

            assertThat(respuesta.statusCode()).as("%s %s", pedido.metodo(), pedido.ruta()).isEqualTo(423);
            assertThat(pedidos.json(respuesta).get("codigo").asText()).isEqualTo("CONSOLA_BLOQUEADA");
        }
    }

    @Test
    @DisplayName("el token de la consola vale solo en la sesión que lo pidió")
    void elTokenEsDeSuSesion() throws Exception {
        UUID soporte = pedidos.usuario("SOPORTE_TI");
        String token = pedidos.desbloquear(pedidos.sesionDe(soporte));

        HttpResponse<String> respuesta = pedidos.enviar("GET", "/api/v1/soporte-ti/funcionalidades",
                pedidos.sesionDe(soporte), null, Map.of("X-Consola-Token", token));

        assertThat(respuesta.statusCode()).isEqualTo(423);
    }

    @Test
    @DisplayName("contraseña mal: 401; a la sexta en 15 minutos, 429 aunque sea la buena; todo queda auditado")
    void contrasenaMalYBloqueo() throws Exception {
        UUID soporte = pedidos.usuario("SOPORTE_TI");
        String sesion = pedidos.sesionDe(soporte);
        for (int intento = 0; intento < 5; intento++) {
            HttpResponse<String> mal = desbloquearCon(sesion, "mala");
            assertThat(mal.statusCode()).isEqualTo(401);
            assertThat(pedidos.json(mal).get("codigo").asText())
                    .as("la app distingue la contraseña mal de una sesión vencida").isEqualTo("CONTRASENA_INCORRECTA");
        }

        assertThat(desbloquearCon(sesion, PedidosDePrueba.CONTRASENA).statusCode()).isEqualTo(429);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.cambios_auditados WHERE actor_id = ? "
                        + "AND tipo = 'DESBLOQUEO' AND despues->>'resultado' = 'CONTRASENA_INCORRECTA'", Integer.class,
                soporte)).isEqualTo(5);
    }

    @Test
    @DisplayName("listar: el catálogo entero, con el estado por rol y versión 0 si nadie lo cambió")
    void listarElCatalogo() throws Exception {
        String sesion = pedidos.sesionDe(pedidos.usuario("SOPORTE_TI"));
        String token = pedidos.desbloquear(sesion);

        HttpResponse<String> respuesta = pedidos.enviar("GET", "/api/v1/soporte-ti/funcionalidades", sesion, null,
                Map.of("X-Consola-Token", token));

        assertThat(respuesta.statusCode()).isEqualTo(200);
        JsonNode lista = pedidos.json(respuesta);
        assertThat(lista.size()).isEqualTo(53); // 52 de la spec 002 + lider.cumplimiento (spec 005)
        JsonNode chat = buscar(lista, "chat");
        assertThat(chat.get("critica").asBoolean()).isTrue();
        assertThat(chat.get("prendidaGlobal").asBoolean()).isTrue();
        assertThat(chat.get("porRol").get("TRAINEE").asBoolean()).isTrue();
        assertThat(chat.get("version").asLong()).isZero();
        assertThat(buscar(lista, "chat.grupos").get("madre").asText()).isEqualTo("chat");
    }

    @Test
    @DisplayName("cambio crítico: 428 sin reautenticar; con el token, 200; el mismo token no sirve dos veces")
    void cambioCritico() throws Exception {
        UUID soporte = pedidos.usuario("SOPORTE_TI");
        String sesion = pedidos.sesionDe(soporte);
        String consola = pedidos.desbloquear(sesion);
        Map<String, Object> apagarElMuro = cambio(false, Map.of(), "El muro vuelve pronto", 0);

        HttpResponse<String> sinReautenticar = pedidos.enviar("PUT", "/api/v1/soporte-ti/funcionalidades/muro", sesion,
                apagarElMuro, Map.of("X-Consola-Token", consola));
        assertThat(sinReautenticar.statusCode()).isEqualTo(428);
        assertThat(pedidos.json(sinReautenticar).get("codigo").asText()).isEqualTo("REAUTENTICACION_REQUERIDA");

        String reautenticacion = pedidos.reautenticar(sesion, consola);
        Map<String, String> conToken = Map.of("X-Consola-Token", consola, "X-Reautenticacion", reautenticacion);
        HttpResponse<String> conReautenticacion = pedidos.enviar("PUT", "/api/v1/soporte-ti/funcionalidades/muro",
                sesion, apagarElMuro, conToken);
        assertThat(conReautenticacion.statusCode()).isEqualTo(200);
        JsonNode muro = pedidos.json(conReautenticacion);
        assertThat(muro.get("prendidaGlobal").asBoolean()).isFalse();
        assertThat(muro.get("aviso").asText()).isEqualTo("El muro vuelve pronto");
        assertThat(muro.get("actualizadoPor").get("id").asText()).isEqualTo(soporte.toString());

        long version = muro.get("version").asLong();
        HttpResponse<String> reusado = pedidos.enviar("PUT", "/api/v1/soporte-ti/funcionalidades/muro", sesion,
                cambio(true, Map.of(), null, version), conToken);
        assertThat(reusado.statusCode()).isEqualTo(428);
    }

    @Test
    @DisplayName("versión vieja: 409; sin motivo o con una clave anti-bloqueo: 400")
    void conflictosYPedidosInvalidos() throws Exception {
        String sesion = pedidos.sesionDe(pedidos.usuario("SOPORTE_TI"));
        Map<String, String> consola = Map.of("X-Consola-Token", pedidos.desbloquear(sesion));
        String ruta = "/api/v1/soporte-ti/funcionalidades/chat.audio";

        assertThat(pedidos.enviar("PUT", ruta, sesion, cambio(true, Map.of("TRAINEE", false), null, 0), consola)
                .statusCode()).isEqualTo(200);
        assertThat(pedidos.enviar("PUT", ruta, sesion, cambio(true, Map.of(), null, 0), consola).statusCode())
                .as("la versión 0 ya no es la vigente").isEqualTo(409);

        Map<String, Object> sinMotivo = new LinkedHashMap<>(cambio(true, Map.of(), null, 0));
        sinMotivo.put("motivo", " ");
        assertThat(pedidos.enviar("PUT", "/api/v1/soporte-ti/funcionalidades/radar", sesion, sinMotivo, consola)
                .statusCode()).isEqualTo(400);
        assertThat(pedidos.enviar("PUT", "/api/v1/soporte-ti/funcionalidades/login", sesion,
                cambio(false, Map.of(), null, 0), consola).statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("cada cambio deja su fila MANUAL con antes, después, motivo y dispositivo; la base no deja editarla ni "
            + "borrarla")
    void auditoriaAppendOnly() throws Exception {
        UUID soporte = pedidos.usuario("SOPORTE_TI");
        String sesion = pedidos.sesionDe(soporte);
        Map<String, String> consola = Map.of("X-Consola-Token", pedidos.desbloquear(sesion));

        pedidos.enviar("PUT", "/api/v1/soporte-ti/funcionalidades/chat.audio", sesion, cambio(true,
                Map.of("TRAINEE", false, "MENTOR", true, "IT_SUPPORT", true), "Notas de voz mañana", 0), consola);

        Map<String, Object> fila = jdbc.queryForMap("SELECT id, antes::text AS antes, despues::text AS despues, motivo, "
                + "dispositivo FROM renaser.cambios_auditados WHERE actor_id = ? AND tipo = 'MANUAL'", soporte);
        assertThat(fila.get("motivo")).isEqualTo("Prueba de la consola");
        assertThat(fila.get("dispositivo")).isEqualTo("PruebaDePlataforma/1.0");
        assertThat((String) fila.get("antes")).contains("\"apagadaPara\": []");
        assertThat((String) fila.get("despues")).contains("\"TRAINEE\"").contains("Notas de voz mañana");
        Object id = fila.get("id");
        assertThatThrownBy(() -> jdbc.update("UPDATE renaser.cambios_auditados SET motivo = 'otro' WHERE id = ?", id))
                .rootCause().hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM renaser.cambios_auditados WHERE id = ?", id))
                .rootCause().hasMessageContaining("append-only");

        HttpResponse<String> auditoria = pedidos.enviar("GET", "/api/v1/soporte-ti/auditoria?clave=chat.audio&size=10&actor=" + soporte,
                sesion, null, consola);
        assertThat(auditoria.statusCode()).isEqualTo(200);
        JsonNode primera = pedidos.json(auditoria).get("contenido").get(0);
        assertThat(primera.get("tipo").asText()).isEqualTo("MANUAL");
        assertThat(primera.get("actor").get("nombre").asText()).isEqualTo("Fixture SOPORTE_TI");
        assertThat(primera.get("despues").get("apagadaPara").get(0).asText()).isEqualTo("TRAINEE");
        assertThat(primera.get("despues").get("porRol").get("TRAINEE").asBoolean()).isFalse();
        assertThat(primera.get("despues").get("porRol").get("MENTOR").asBoolean()).isTrue();
        assertThat(primera.get("antes").get("prendidaGlobal").asBoolean()).isTrue();
        assertThat(primera.get("despues").has("aviso")).isTrue();
        assertThat(pedidos.json(auditoria).get("pagina").asInt()).isZero();
        assertThat(pedidos.json(auditoria).get("total").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("un cambio crítico avisa por push a Soporte TI, al Admin y al Alquimista; uno que no lo es, solo a "
            + "Soporte TI (FR-008)")
    void avisaPorPush() throws Exception {
        UUID soporte = pedidos.usuario("SOPORTE_TI");
        UUID admin = pedidos.usuario("ADMIN");
        String sesion = pedidos.sesionDe(soporte);
        String consola = pedidos.desbloquear(sesion);

        pedidos.enviar("PUT", "/api/v1/soporte-ti/funcionalidades/chat.audio", sesion,
                cambio(true, Map.of("TRAINEE", false), null, 0), Map.of("X-Consola-Token", consola));
        pedidos.esperarElOutbox(inicio);
        assertThat(avisosDePlataforma(soporte)).isEqualTo(1);
        assertThat(avisosDePlataforma(admin)).as("no crítico: el Admin no se entera").isZero();

        String reautenticacion = pedidos.reautenticar(sesion, consola);
        pedidos.enviar("PUT", "/api/v1/soporte-ti/funcionalidades/muro", sesion, cambio(false, Map.of(), null, 0),
                Map.of("X-Consola-Token", consola, "X-Reautenticacion", reautenticacion));
        pedidos.esperarElOutbox(inicio);
        assertThat(avisosDePlataforma(soporte)).isEqualTo(2);
        assertThat(avisosDePlataforma(admin)).as("crítico: el Admin se entera").isEqualTo(1);
    }

    private int avisosDePlataforma(UUID usuario) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.notificaciones WHERE usuario_id = ? "
                + "AND tipo = 'OPERACION_PLATAFORMA'", Integer.class, usuario);
    }

    @Test
    @DisplayName("el circuito de la IA responde 200 aunque el circuito todavía no exista (lista vacía)")
    void circuitoSinRag() throws Exception {
        String sesion = pedidos.sesionDe(pedidos.usuario("SOPORTE_TI"));
        Map<String, String> consola = Map.of("X-Consola-Token", pedidos.desbloquear(sesion));

        HttpResponse<String> respuesta = pedidos.enviar("GET", "/api/v1/soporte-ti/ia/circuito", sesion, null, consola);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(pedidos.json(respuesta).isArray()).isTrue();
    }

    @Test
    @DisplayName("GET /funcionalidades/mias: lo apagado para esa sesión (las hijas también), con ETag y 304")
    void misFuncionalidadesConEtag() throws Exception {
        String soporte = pedidos.sesionDe(pedidos.usuario("SOPORTE_TI"));
        String consola = pedidos.desbloquear(soporte);
        pedidos.enviar("PUT", "/api/v1/soporte-ti/funcionalidades/muro.reacciones", soporte,
                cambio(true, Map.of("TRAINEE", false), "Reacciones en mantenimiento", 0),
                Map.of("X-Consola-Token", consola));
        String aprendiz = pedidos.sesionDe(pedidos.usuario("APRENDIZ"));

        HttpResponse<String> primera = pedidos.enviar("GET", "/api/v1/funcionalidades/mias", aprendiz, null);

        assertThat(primera.statusCode()).isEqualTo(200);
        JsonNode cuerpo = pedidos.json(primera);
        assertThat(cuerpo.get("version").asLong()).isPositive();
        assertThat(cuerpo.get("apagadas")).hasSize(1);
        assertThat(cuerpo.get("apagadas").get(0).get("clave").asText()).isEqualTo("muro.reacciones");
        assertThat(cuerpo.get("apagadas").get(0).get("aviso").asText()).isEqualTo("Reacciones en mantenimiento");
        String etag = primera.headers().firstValue("ETag").orElseThrow();
        assertThat(etag).startsWith("\"v");

        HttpResponse<String> segunda = pedidos.enviar("GET", "/api/v1/funcionalidades/mias", aprendiz, null,
                Map.of("If-None-Match", etag));
        assertThat(segunda.statusCode()).isEqualTo(304);

        String mentor = pedidos.sesionDe(pedidos.usuario("MENTOR"));
        HttpResponse<String> delMentor = pedidos.enviar("GET", "/api/v1/funcionalidades/mias", mentor, null,
                Map.of("If-None-Match", etag));
        assertThat(delMentor.statusCode()).as("otro rol, otro contenido: el ETag no coincide").isEqualTo(200);
        assertThat(pedidos.json(delMentor).get("apagadas")).isEmpty();
    }

    // ── Pedidos ──────────────────────────────────────────────────────────

    private record Pedido(String metodo, String ruta, Object cuerpo) {
    }

    private static List<Pedido> pedidosDeLaConsola() {
        return List.of(
                new Pedido("POST", "/api/v1/soporte-ti/desbloqueo",
                        Map.of("contrasena", PedidosDePrueba.CONTRASENA, "para", "CONSOLA")),
                new Pedido("GET", "/api/v1/soporte-ti/funcionalidades", null),
                new Pedido("PUT", "/api/v1/soporte-ti/funcionalidades/chat.grupos", cambio(true, Map.of(), null, 0)),
                new Pedido("GET", "/api/v1/soporte-ti/auditoria", null),
                new Pedido("GET", "/api/v1/soporte-ti/ia/circuito", null));
    }

    private static Map<String, Object> cambio(boolean prendida, Map<String, Boolean> porRol, String aviso,
                                              long version) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("prendidaGlobal", prendida);
        cuerpo.put("porRol", porRol);
        cuerpo.put("aviso", aviso);
        cuerpo.put("motivo", "Prueba de la consola");
        cuerpo.put("version", version);
        return cuerpo;
    }

    private HttpResponse<String> desbloquearCon(String sesion, String contrasena) throws Exception {
        return pedidos.enviar("POST", "/api/v1/soporte-ti/desbloqueo", sesion,
                Map.of("contrasena", contrasena, "para", "CONSOLA"));
    }

    private static JsonNode buscar(JsonNode lista, String clave) {
        for (JsonNode funcionalidad : lista) {
            if (funcionalidad.get("clave").asText().equals(clave)) {
                return funcionalidad;
            }
        }
        throw new AssertionError("No está " + clave);
    }
}
