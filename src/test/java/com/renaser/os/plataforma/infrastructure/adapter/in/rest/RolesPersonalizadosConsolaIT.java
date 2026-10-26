package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.plataforma.PedidosDePrueba;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La consola de roles personalizados de punta a punta (contrato de la fase 2, §4.5 y §7; D-272): sesión real, los dos
 * interceptores, los servicios de plataforma y users, Postgres con V115/V116 y Redis para los tokens.
 *
 * <p>Autorización negativa en cada endpoint nuevo (ADMIN, ALQUIMISTA, MENTOR, GUÍA, LÍDER, APRENDIZ y un Soporte TI
 * suspendido → 403; sin desbloqueo → 423; crítico sin reautenticar → 428), las reglas que devuelven 400/409 y la
 * auditoría {@code ROL_PERSONALIZADO}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RolesPersonalizadosConsolaIT {

    private static final String RUTA = "/api/v1/soporte-ti/roles";
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

    private PedidosDePrueba pedidos;
    private final Instant inicio = Instant.now();

    @BeforeEach
    void armar() {
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
    }

    @AfterEach
    void limpiar() throws InterruptedException {
        pedidos.esperarElOutbox(inicio);
        jdbc.update("UPDATE renaser.usuarios SET rol_personalizado_id = NULL WHERE rol_personalizado_id IS NOT NULL");
        jdbc.update("DELETE FROM renaser.roles_personalizados");
        pedidos.borrarCuentas();
    }

    private record Pedido(String metodo, String ruta, Object cuerpo) {
    }

    /** Un pedido por endpoint nuevo, con un cuerpo válido: lo único que puede rechazarlos es la autorización. */
    private List<Pedido> pedidosDeLaConsola() {
        String id = UUID.randomUUID().toString();
        return List.of(new Pedido("GET", RUTA, null),
                new Pedido("POST", RUTA, crear("Mentor junior", "MENTOR", List.of("muro"))),
                new Pedido("PUT", RUTA + "/" + id, editar("Mentor junior", List.of("muro"), true, 1)),
                new Pedido("DELETE", RUTA + "/" + id + "?motivo=prueba", null),
                new Pedido("POST", RUTA + "/" + id + "/reemplazar", reemplazar(null)));
    }

    private static Map<String, Object> crear(String nombre, String rolBase, List<String> funcionalidades) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("descripcion", "Prueba");
        cuerpo.put("rolBase", rolBase);
        cuerpo.put("funcionalidades", funcionalidades);
        cuerpo.put("motivo", "Prueba de la consola");
        return cuerpo;
    }

    private static Map<String, Object> editar(String nombre, List<String> funcionalidades, boolean activo,
                                              long version) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("funcionalidades", funcionalidades);
        cuerpo.put("activo", activo);
        cuerpo.put("motivo", "Prueba de la consola");
        cuerpo.put("version", version);
        return cuerpo;
    }

    private static Map<String, Object> reemplazar(UUID destino) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("rolDestinoId", destino == null ? null : destino.toString());
        cuerpo.put("motivo", "Prueba de la consola");
        return cuerpo;
    }

    @Test
    @DisplayName("ningún otro rol entra a los roles de la consola, ni ADMIN ni ALQUIMISTA: 403 en cada endpoint")
    void ningunOtroRolEntra() throws Exception {
        for (String rol : ROLES_SIN_CONSOLA) {
            String sesion = pedidos.sesionDe(pedidos.usuario(rol));
            for (Pedido pedido : pedidosDeLaConsola()) {
                assertThat(pedidos.enviar(pedido.metodo(), pedido.ruta(), sesion, pedido.cuerpo()).statusCode())
                        .as("%s %s como %s", pedido.metodo(), pedido.ruta(), rol).isEqualTo(403);
            }
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.roles_personalizados", Integer.class)).isZero();
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
    @DisplayName("Soporte TI sin desbloquear: 423 CONSOLA_BLOQUEADA en cada endpoint, aun con el cuerpo incompleto")
    void sinDesbloquearEs423() throws Exception {
        String sesion = pedidos.sesionDe(pedidos.usuario("SOPORTE_TI"));
        for (Pedido pedido : pedidosDeLaConsola()) {
            HttpResponse<String> respuesta = pedidos.enviar(pedido.metodo(), pedido.ruta(), sesion, pedido.cuerpo(),
                    Map.of("X-Consola-Token", "inventado"));
            assertThat(respuesta.statusCode()).as("%s %s", pedido.metodo(), pedido.ruta()).isEqualTo(423);
            assertThat(pedidos.json(respuesta).get("codigo").asText()).isEqualTo("CONSOLA_BLOQUEADA");
        }
        assertThat(pedidos.enviar("POST", RUTA, sesion, Map.of()).statusCode()).as("cuerpo vacío sin token")
                .isEqualTo(423);
    }

    @Test
    @DisplayName("borrar y reemplazar en bloque son críticos: 428 REAUTENTICACION_REQUERIDA sin X-Reautenticacion")
    void criticosSinReautenticar() throws Exception {
        String sesion = pedidos.sesionDe(pedidos.usuario("SOPORTE_TI"));
        String consola = pedidos.desbloquear(sesion);
        String id = pedidos.json(pedidos.enviar("POST", RUTA, sesion, crear("Mentor junior", "MENTOR", List.of()),
                Map.of("X-Consola-Token", consola))).get("id").asText();

        HttpResponse<String> borrar = pedidos.enviar("DELETE", RUTA + "/" + id + "?motivo=x", sesion, null,
                Map.of("X-Consola-Token", consola));
        HttpResponse<String> reemplazo = pedidos.enviar("POST", RUTA + "/" + id + "/reemplazar", sesion,
                reemplazar(null), Map.of("X-Consola-Token", consola));

        assertThat(borrar.statusCode()).isEqualTo(428);
        assertThat(pedidos.json(borrar).get("codigo").asText()).isEqualTo("REAUTENTICACION_REQUERIDA");
        assertThat(reemplazo.statusCode()).isEqualTo(428);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.roles_personalizados", Integer.class))
                .isEqualTo(1);

        String reautenticacion = pedidos.reautenticar(sesion, consola);
        assertThat(pedidos.enviar("DELETE", RUTA + "/" + id + "?motivo=Ya%20no%20sirve", sesion, null,
                Map.of("X-Consola-Token", consola, "X-Reautenticacion", reautenticacion)).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.roles_personalizados", Integer.class)).isZero();
    }

    @Test
    @DisplayName("crear, listar y editar: 400 por base Soporte TI, clave fuera de la base o nombre repetido; 409 por "
            + "versión vieja; cada cambio deja su fila ROL_PERSONALIZADO, legible en la auditoría con la clave rp:<id>")
    void crearListarYEditar() throws Exception {
        UUID soporte = pedidos.usuario("SOPORTE_TI");
        String sesion = pedidos.sesionDe(soporte);
        Map<String, String> consola = Map.of("X-Consola-Token", pedidos.desbloquear(sesion));

        HttpResponse<String> creado = pedidos.enviar("POST", RUTA, sesion,
                crear("Mentor junior", "MENTOR", List.of("muro", "chat")), consola);
        assertThat(creado.statusCode()).isEqualTo(201);
        JsonNode rol = pedidos.json(creado);
        String id = rol.get("id").asText();
        assertThat(rol.get("clave").asText()).isEqualTo("rp:" + id);
        assertThat(rol.get("rolBase").asText()).isEqualTo("MENTOR");
        assertThat(rol.get("activo").asBoolean()).isTrue();
        assertThat(rol.get("version").asLong()).isEqualTo(1);
        assertThat(rol.get("personas").asLong()).isZero();
        assertThat(rol.get("actualizadoPor").get("id").asText()).isEqualTo(soporte.toString());

        assertThat(pedidos.enviar("POST", RUTA, sesion, crear("Técnico", "IT_SUPPORT", List.of()), consola)
                .statusCode()).as("base Soporte TI").isEqualTo(400);
        assertThat(pedidos.enviar("POST", RUTA, sesion, crear("Gestor", "MENTOR", List.of("eventos.gestion")),
                consola).statusCode()).as("clave que MENTOR no usa").isEqualTo(400);
        HttpResponse<String> repetido = pedidos.enviar("POST", RUTA, sesion, crear("MENTOR JUNIOR", "TRAINEE",
                List.of()), consola);
        assertThat(repetido.statusCode()).as("nombre repetido").isEqualTo(400);
        assertThat(pedidos.json(repetido).get("codigo").asText()).isEqualTo("NOMBRE_REPETIDO");
        HttpResponse<String> deRolBase = pedidos.enviar("POST", RUTA, sesion, crear("Mentor", "TRAINEE", List.of()),
                consola);
        assertThat(deRolBase.statusCode()).as("nombre de un rol base").isEqualTo(400);
        assertThat(pedidos.json(deRolBase).get("codigo").asText()).isEqualTo("NOMBRE_REPETIDO");

        JsonNode lista = pedidos.json(pedidos.enviar("GET", RUTA, sesion, null, consola));
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("funcionalidades").toString()).isEqualTo("[\"chat\",\"muro\"]");

        HttpResponse<String> versionVieja = pedidos.enviar("PUT", RUTA + "/" + id, sesion,
                editar("Mentor junior", List.of("muro"), false, 7), consola);
        assertThat(versionVieja.statusCode()).as("versión vieja").isEqualTo(409);
        assertThat(pedidos.json(versionVieja).get("codigo").asText()).isEqualTo("VERSION_CAMBIADA");
        assertThat(pedidos.json(versionVieja).get("message").asText()).isNotBlank();
        assertThat(pedidos.json(versionVieja).has("funcionalidad")).isFalse();
        HttpResponse<String> desactivado = pedidos.enviar("PUT", RUTA + "/" + id, sesion,
                editar("Mentor junior", List.of("muro"), false, 1), consola);
        assertThat(desactivado.statusCode()).isEqualTo(200);
        assertThat(pedidos.json(desactivado).get("activo").asBoolean()).isFalse();
        assertThat(pedidos.json(desactivado).get("version").asLong()).isEqualTo(2);

        JsonNode auditoria = pedidos.json(pedidos.enviar("GET", "/api/v1/soporte-ti/auditoria?clave=rp:" + id,
                sesion, null, consola));
        assertThat(auditoria.get("total").asLong()).isEqualTo(2);
        JsonNode ultima = auditoria.get("contenido").get(0);
        assertThat(ultima.get("tipo").asText()).isEqualTo("ROL_PERSONALIZADO");
        assertThat(ultima.get("antes").get("activo").asBoolean()).isTrue();
        assertThat(ultima.get("despues").get("activo").asBoolean()).isFalse();
        assertThat(ultima.get("despues").get("funcionalidades").toString()).isEqualTo("[\"muro\"]");
    }

    @Test
    @DisplayName("hasta 20 activos: el 21 es 400")
    void veinteActivos() throws Exception {
        String sesion = pedidos.sesionDe(pedidos.usuario("SOPORTE_TI"));
        Map<String, String> consola = Map.of("X-Consola-Token", pedidos.desbloquear(sesion));
        for (int i = 1; i <= 20; i++) {
            assertThat(pedidos.enviar("POST", RUTA, sesion, crear("Rol " + i, "TRAINEE", List.of()), consola)
                    .statusCode()).isEqualTo(201);
        }

        HttpResponse<String> vigesimoPrimero = pedidos.enviar("POST", RUTA, sesion,
                crear("Rol 21", "TRAINEE", List.of()), consola);

        assertThat(vigesimoPrimero.statusCode()).isEqualTo(400);
        assertThat(pedidos.json(vigesimoPrimero).get("message").asText()).contains("20");
        assertThat(pedidos.json(vigesimoPrimero).get("codigo").asText()).isEqualTo("MAXIMO_DE_ROLES");
    }
}
