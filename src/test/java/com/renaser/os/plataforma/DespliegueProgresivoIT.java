package com.renaser.os.plataforma;

import com.fasterxml.jackson.databind.JsonNode;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.plataforma.application.services.CopiaDeDespliegues;
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
 * El despliegue progresivo de punta a punta (spec 002, US4; contrato fase 2 §3 y §7): sesión real, los dos
 * interceptores, la regla {@code @Order(10)} sobre la copia en memoria, Postgres con V113 y Redis.
 *
 * <p>El escenario central: Soporte TI empieza un despliegue al 0 % (crítico: pide contraseña) y suma una probadora; la
 * probadora recibe 200, otra persona 404 {@code FUNCIONALIDAD_NO_DISPONIBLE} y la ve en {@code noDisponibles}; al
 * subir a 100 % llega a todos. Además: una madre en despliegue esconde a sus hijas, la autorización negativa de cada
 * endpoint nuevo (403 / 423 / 428), 409, 400, los probadores (404 / 409 / 204), la búsqueda de personas, la auditoría y
 * el push.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class DespliegueProgresivoIT {

    private static final List<String> ROLES_SIN_CONSOLA = List.of("ADMIN", "ALQUIMISTA", "MENTOR", "APRENDIZ",
            "GUIA_APRENDIZ", "LIDER_MENTORES");
    private static final String DESPLIEGUE = "/api/v1/soporte-ti/funcionalidades/%s/despliegue";

    @LocalServerPort
    private int puerto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private CopiaDeEstados copiaDeEstados;
    @Autowired
    private CopiaDeDespliegues copiaDeDespliegues;

    private PedidosDePrueba pedidos;
    private UUID soporteId;
    private String soporte;
    private String consola;
    private final Instant inicio = Instant.now();

    @BeforeEach
    void armar() throws Exception {
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
        soporteId = pedidos.usuario("SOPORTE_TI");
        soporte = pedidos.sesionDe(soporteId);
        consola = pedidos.desbloquear(soporte);
    }

    @AfterEach
    void limpiar() throws InterruptedException {
        pedidos.esperarElOutbox(inicio);
        jdbc.update("DELETE FROM renaser.funcionalidades_despliegue");
        jdbc.update("DELETE FROM renaser.funcionalidades_estado");
        copiaDeDespliegues.recargar();
        copiaDeEstados.recargar();
        pedidos.borrarCuentas();
    }

    @Test
    @DisplayName("al 0 % con una probadora: ella 200, otra persona 404 FUNCIONALIDAD_NO_DISPONIBLE y la clave en "
            + "noDisponibles; a 100 % todos 200; queda auditado y avisa por push a Soporte TI")
    void ceroConProbadoraYDespuesParaTodos() throws Exception {
        UUID admin = pedidos.usuario("ADMIN");
        UUID probadoraId = pedidos.usuario("APRENDIZ");
        String probadora = pedidos.sesionDe(probadoraId);
        String otra = pedidos.sesionDe(pedidos.usuario("APRENDIZ"));

        HttpResponse<String> sinContrasena = cambiar("chat.grupos", 0, 0L, false);
        assertThat(sinContrasena.statusCode()).as("empezar al 0 %% es crítico").isEqualTo(428);
        assertThat(pedidos.json(sinContrasena).get("codigo").asText()).isEqualTo("REAUTENTICACION_REQUERIDA");

        JsonNode empezado = pedidos.json(exigir200(cambiar("chat.grupos", 0, 0L, true)));
        assertThat(empezado.get("enDespliegue").asBoolean()).isTrue();
        assertThat(empezado.get("porcentaje").asInt()).isZero();
        assertThat(empezado.get("metricas")).hasSize(24);
        JsonNode conProbadora = pedidos.json(exigir200(sumarProbadora("chat.grupos", probadoraId)));
        assertThat(conProbadora.get("probadores")).singleElement().satisfies(p -> {
            assertThat(p.get("id").asText()).isEqualTo(probadoraId.toString());
            assertThat(p.get("email").asText()).isEqualTo(probadoraId + "@renaser.test");
        });

        assertThat(pedidos.enviar("GET", "/api/v1/me/cells", probadora, null).statusCode()).isEqualTo(200);
        HttpResponse<String> deOtra = pedidos.enviar("GET", "/api/v1/me/cells", otra, null);
        assertThat(deOtra.statusCode()).isEqualTo(404);
        JsonNode error = pedidos.json(deOtra);
        assertThat(error.get("codigo").asText()).isEqualTo("FUNCIONALIDAD_NO_DISPONIBLE");
        assertThat(error.get("funcionalidad").asText()).isEqualTo("chat.grupos");
        assertThat(error.get("message").asText()).isEqualTo("Esta función todavía no está disponible para tu cuenta.");
        JsonNode miasDeOtra = mias(otra);
        assertThat(textos(miasDeOtra.get("noDisponibles"))).contains("chat.grupos").doesNotContain("chat");
        assertThat(miasDeOtra.get("apagadas")).isEmpty();
        assertThat(textos(mias(probadora).get("noDisponibles"))).doesNotContain("chat.grupos");
        JsonNode enLaLista = buscar(lista(), "chat.grupos").get("despliegue");
        assertThat(enLaLista.get("porcentaje").asInt()).isZero();
        assertThat(enLaLista.get("probadores").asInt()).isEqualTo(1);
        assertThat(buscar(lista(), "chat").get("despliegue").isNull()).isTrue();

        long version = pedidos.json(pedidos.enviar("GET", DESPLIEGUE.formatted("chat.grupos"), soporte, null,
                conConsola())).get("version").asLong();
        JsonNode paraTodos = pedidos.json(exigir200(cambiar("chat.grupos", 100, version, false)));
        assertThat(paraTodos.get("enDespliegue").asBoolean()).isFalse();
        assertThat(paraTodos.get("porcentaje").asInt()).isEqualTo(100);
        assertThat(pedidos.enviar("GET", "/api/v1/me/cells", otra, null).statusCode()).isEqualTo(200);
        assertThat(mias(otra).get("noDisponibles")).isEmpty();

        List<String> antes = jdbc.queryForList("SELECT antes::text FROM renaser.cambios_auditados WHERE actor_id = ? "
                + "AND tipo = 'MANUAL' AND clave = 'chat.grupos' ORDER BY creado_en", String.class, soporteId);
        assertThat(antes).as("empezar, sumar la probadora y terminar").hasSize(3);
        assertThat(antes.getFirst()).isEqualTo("{\"despliegue\": null}");
        assertThat(jdbc.queryForObject("SELECT motivo FROM renaser.cambios_auditados WHERE actor_id = ? AND "
                + "motivo LIKE 'Probadora sumada%'", String.class, soporteId)).contains(probadoraId.toString());
        pedidos.esperarElOutbox(inicio);
        assertThat(avisosDePlataforma(soporteId)).as("empezar y terminar avisan; sumar una probadora no").isEqualTo(2);
        assertThat(avisosDePlataforma(admin)).as("chat.grupos no es crítica").isZero();
    }

    @Test
    @DisplayName("una madre en despliegue esconde a todas sus hijas: «chat» al 0 % deja fuera chat.grupos (404 con la madre)")
    void laMadreEscondeALasHijas() throws Exception {
        String otra = pedidos.sesionDe(pedidos.usuario("APRENDIZ"));

        exigir200(cambiar("chat", 0, null, true));

        HttpResponse<String> respuesta = pedidos.enviar("GET", "/api/v1/me/cells", otra, null);
        assertThat(respuesta.statusCode()).isEqualTo(404);
        assertThat(pedidos.json(respuesta).get("funcionalidad").asText()).isEqualTo("chat");
        assertThat(textos(mias(otra).get("noDisponibles"))).contains("chat", "chat.grupos", "chat.global",
                "chat.soporte", "chat.directos", "chat.audio", "chat.fotos");
        assertThat(pedidos.enviar("GET", "/api/v1/wall", otra, null).statusCode())
                .as("el muro no es del chat").isNotEqualTo(404);
    }

    @Test
    @DisplayName("ningún otro rol usa los endpoints del despliegue, ni ADMIN ni ALQUIMISTA, ni Soporte TI suspendido: 403")
    void autorizacionNegativa() throws Exception {
        List<String> sesionesSinConsola = new ArrayList<>();
        ROLES_SIN_CONSOLA.forEach(rol -> sesionesSinConsola.add(pedidos.sesionDe(pedidos.usuario(rol))));
        sesionesSinConsola.add(pedidos.sesionDe(pedidos.usuario("SOPORTE_TI", "SUSPENDIDO")));
        for (String sesion : sesionesSinConsola) {
            for (Pedido pedido : pedidosDelDespliegue()) {
                assertThat(pedidos.enviar(pedido.metodo(), pedido.ruta(), sesion, pedido.cuerpo()).statusCode())
                        .as("%s %s", pedido.metodo(), pedido.ruta()).isEqualTo(403);
            }
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.funcionalidades_despliegue", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("Soporte TI sin desbloquear: 423 CONSOLA_BLOQUEADA en cada endpoint del despliegue")
    void sinDesbloquear() throws Exception {
        String sesion = pedidos.sesionDe(pedidos.usuario("SOPORTE_TI"));
        for (Pedido pedido : pedidosDelDespliegue()) {
            HttpResponse<String> respuesta = pedidos.enviar(pedido.metodo(), pedido.ruta(), sesion, pedido.cuerpo(),
                    Map.of("X-Consola-Token", "inventado"));

            assertThat(respuesta.statusCode()).as("%s %s", pedido.metodo(), pedido.ruta()).isEqualTo(423);
            assertThat(pedidos.json(respuesta).get("codigo").asText()).isEqualTo("CONSOLA_BLOQUEADA");
        }
    }

    @Test
    @DisplayName("409 con la versión vieja; 400 sin motivo, con porcentaje 101 o con una clave anti-bloqueo")
    void conflictosYPedidosInvalidos() throws Exception {
        exigir200(cambiar("radar", 25, 0L, false));

        assertThat(cambiar("radar", 50, 0L, false).statusCode()).as("la versión 0 ya no es la vigente").isEqualTo(409);
        Map<String, Object> sinMotivo = new LinkedHashMap<>(Map.of("porcentaje", 50, "motivo", " "));
        assertThat(pedidos.enviar("PUT", DESPLIEGUE.formatted("radar"), soporte, sinMotivo, conConsola()).statusCode())
                .isEqualTo(400);
        assertThat(cambiar("radar", 101, null, false).statusCode()).isEqualTo(400);
        assertThat(cambiar("login", 25, null, false).statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("una funcionalidad que se usa sin cuenta (registro) no se despliega: 409 DESPLIEGUE_SIN_CUENTA y "
            + "desplegable false en el GET")
    void sinCuentaNoSeDespliega() throws Exception {
        HttpResponse<String> respuesta = cambiar("registro", 25, null, true);

        assertThat(respuesta.statusCode()).isEqualTo(409);
        JsonNode error = pedidos.json(respuesta);
        assertThat(error.get("codigo").asText()).isEqualTo("DESPLIEGUE_SIN_CUENTA");
        assertThat(error.get("funcionalidad").asText()).isEqualTo("registro");
        assertThat(error.get("message").asText())
                .isEqualTo("Esta funcionalidad se usa sin cuenta; un despliegue no tendría efecto.");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.funcionalidades_despliegue", Integer.class))
                .isZero();
        assertThat(pedidos.json(pedidos.enviar("GET", DESPLIEGUE.formatted("registro"), soporte, null, conConsola()))
                .get("desplegable").asBoolean()).isFalse();
        assertThat(pedidos.json(pedidos.enviar("GET", DESPLIEGUE.formatted("chat.grupos"), soporte, null,
                conConsola())).get("desplegable").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("probadores: 404 si la persona no existe, 409 si no hay despliegue, quitar da 204")
    void probadores() throws Exception {
        UUID aprendiz = pedidos.usuario("APRENDIZ");

        assertThat(sumarProbadora("radar", aprendiz).statusCode()).as("radar no está en despliegue").isEqualTo(409);
        exigir200(cambiar("radar", 25, null, false));
        assertThat(sumarProbadora("radar", UUID.randomUUID()).statusCode()).isEqualTo(404);
        exigir200(sumarProbadora("radar", aprendiz));

        HttpResponse<String> quitar = pedidos.enviar("DELETE", DESPLIEGUE.formatted("radar") + "/probadores/" + aprendiz,
                soporte, null, conConsola());

        assertThat(quitar.statusCode()).isEqualTo(204);
        assertThat(pedidos.json(pedidos.enviar("GET", DESPLIEGUE.formatted("radar"), soporte, null, conConsola()))
                .get("probadores")).isEmpty();
    }

    @Test
    @DisplayName("buscar personas: por nombre sin tildes ni mayúsculas, solo activas, con correo y rol")
    void buscarPersonas() throws Exception {
        String marca = "qz" + UUID.randomUUID().toString().substring(0, 6);
        UUID begona = pedidos.usuario("MENTOR");
        UUID suspendida = pedidos.usuario("APRENDIZ", "SUSPENDIDO");
        jdbc.update("UPDATE renaser.usuarios SET nombre_completo = ? WHERE id = ?", "Begoña Núñez " + marca, begona);
        jdbc.update("UPDATE renaser.usuarios SET nombre_completo = ? WHERE id = ?", "Begoña Suspendida " + marca,
                suspendida);

        HttpResponse<String> respuesta = pedidos.enviar("GET", "/api/v1/soporte-ti/personas?q=BEGONA%20NUNEZ%20"
                + marca, soporte, null, conConsola());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(pedidos.json(respuesta)).singleElement().satisfies(persona -> {
            assertThat(persona.get("id").asText()).isEqualTo(begona.toString());
            assertThat(persona.get("nombre").asText()).isEqualTo("Begoña Núñez " + marca);
            assertThat(persona.get("email").asText()).isEqualTo(begona + "@renaser.test");
            assertThat(persona.get("rol").asText()).isEqualTo("MENTOR");
        });
        assertThat(pedidos.json(pedidos.enviar("GET", "/api/v1/soporte-ti/personas?q=" + marca, soporte, null,
                conConsola()))).as("la suspendida no aparece").hasSize(1);
    }

    // ── Pedidos ──────────────────────────────────────────────────────────

    private HttpResponse<String> cambiar(String clave, int porcentaje, Long version, boolean conContrasena)
            throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("porcentaje", porcentaje);
        cuerpo.put("motivo", "Prueba del despliegue");
        cuerpo.put("version", version);
        Map<String, String> cabeceras = new LinkedHashMap<>(conConsola());
        if (conContrasena) {
            cabeceras.put("X-Reautenticacion", pedidos.reautenticar(soporte, consola));
        }
        return pedidos.enviar("PUT", DESPLIEGUE.formatted(clave), soporte, cuerpo, cabeceras);
    }

    private HttpResponse<String> sumarProbadora(String clave, UUID persona) throws Exception {
        return pedidos.enviar("PUT", DESPLIEGUE.formatted(clave) + "/probadores/" + persona, soporte,
                Map.of("motivo", "Prueba"), conConsola());
    }

    private JsonNode mias(String sesion) throws Exception {
        HttpResponse<String> respuesta = pedidos.enviar("GET", "/api/v1/funcionalidades/mias", sesion, null);
        assertThat(respuesta.statusCode()).isEqualTo(200);
        return pedidos.json(respuesta);
    }

    private JsonNode lista() throws Exception {
        return pedidos.json(exigir200(pedidos.enviar("GET", "/api/v1/soporte-ti/funcionalidades", soporte, null,
                conConsola())));
    }

    private int avisosDePlataforma(UUID usuario) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.notificaciones WHERE usuario_id = ? "
                + "AND tipo = 'OPERACION_PLATAFORMA'", Integer.class, usuario);
    }

    private Map<String, String> conConsola() {
        return Map.of("X-Consola-Token", consola);
    }

    private static HttpResponse<String> exigir200(HttpResponse<String> respuesta) {
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        return respuesta;
    }

    private static List<String> textos(JsonNode arreglo) {
        List<String> textos = new ArrayList<>();
        arreglo.forEach(nodo -> textos.add(nodo.asText()));
        return textos;
    }

    private static JsonNode buscar(JsonNode lista, String clave) {
        for (JsonNode funcionalidad : lista) {
            if (funcionalidad.get("clave").asText().equals(clave)) {
                return funcionalidad;
            }
        }
        throw new AssertionError("No está " + clave);
    }

    private record Pedido(String metodo, String ruta, Object cuerpo) {
    }

    private static List<Pedido> pedidosDelDespliegue() {
        String probadora = "/probadores/" + UUID.randomUUID();
        return List.of(
                new Pedido("GET", DESPLIEGUE.formatted("chat.grupos"), null),
                new Pedido("PUT", DESPLIEGUE.formatted("chat.grupos"), Map.of("porcentaje", 5, "motivo", "x")),
                new Pedido("PUT", DESPLIEGUE.formatted("chat.grupos") + probadora, Map.of()),
                new Pedido("DELETE", DESPLIEGUE.formatted("chat.grupos") + probadora, null),
                new Pedido("GET", "/api/v1/soporte-ti/personas?q=fixture", null));
    }
}
