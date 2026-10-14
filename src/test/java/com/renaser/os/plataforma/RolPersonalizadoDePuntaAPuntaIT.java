package com.renaser.os.plataforma;

import com.fasterxml.jackson.databind.JsonNode;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.plataforma.application.services.CopiaDeRolesPersonalizados;
import com.renaser.os.plataforma.infrastructure.adapter.in.redis.SuscripcionAlCanalDeRolesPersonalizados;
import com.renaser.os.plataforma.infrastructure.adapter.out.redis.CanalDeRolesPersonalizados;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.RolesPersonalizadosFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El escenario de la spec 002 para roles personalizados (US7; contrato de la fase 2, §7; D-269), de punta a punta:
 * Soporte TI crea «Mentor junior» (base MENTOR) sin {@code eventos} desde la consola, Administración se lo asigna a un
 * mentor, y ese mentor recibe 404 {@code FUNCIONALIDAD_NO_DISPONIBLE} en un endpoint de eventos y ve {@code eventos} en
 * sus {@code noDisponibles}; otro mentor, sin rol personalizado, sigue con 200. Al desactivar el rol, vuelve todo lo de
 * la base. Además: la otra instancia se entera por Redis, y el ciclo de reemplazar en bloque y borrar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RolPersonalizadoDePuntaAPuntaIT {

    private static final String RUTA = "/api/v1/soporte-ti/roles";

    @LocalServerPort
    private int puerto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private RolesPersonalizadosFinder finder;
    @Autowired
    private RedisConnectionFactory conexiones;

    private PedidosDePrueba pedidos;
    private String soporte;
    private Map<String, String> consola;
    private String admin;
    private CopiaDeRolesPersonalizados copiaDeOtraInstancia;
    private RedisMessageListenerContainer suscripcionDeOtraInstancia;
    private final List<UUID> mentores = new ArrayList<>();
    private final Instant inicio = Instant.now();

    @BeforeEach
    void armar() throws Exception {
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
        soporte = pedidos.sesionDe(pedidos.usuario("SOPORTE_TI"));
        consola = Map.of("X-Consola-Token", pedidos.desbloquear(soporte));
        admin = pedidos.sesionDe(pedidos.usuario("ADMIN"));
        copiaDeOtraInstancia = new CopiaDeRolesPersonalizados(finder);
        copiaDeOtraInstancia.recargar();
        suscripcionDeOtraInstancia = new RedisMessageListenerContainer();
        suscripcionDeOtraInstancia.setConnectionFactory(conexiones);
        suscripcionDeOtraInstancia.addMessageListener(
                SuscripcionAlCanalDeRolesPersonalizados.oyentePara(copiaDeOtraInstancia),
                new ChannelTopic(CanalDeRolesPersonalizados.NOMBRE));
        suscripcionDeOtraInstancia.afterPropertiesSet();
        suscripcionDeOtraInstancia.start();
    }

    @AfterEach
    void limpiar() throws Exception {
        suscripcionDeOtraInstancia.stop();
        suscripcionDeOtraInstancia.destroy();
        pedidos.esperarElOutbox(inicio);
        jdbc.update("UPDATE renaser.usuarios SET rol_personalizado_id = NULL WHERE rol_personalizado_id IS NOT NULL");
        jdbc.update("DELETE FROM renaser.roles_personalizados");
        jdbc.update("DELETE FROM renaser.historial_rol_participante WHERE rige_desde >= ?",
                java.sql.Timestamp.from(inicio));
        pedidos.borrarCuentas();
    }

    private UUID mentor() {
        UUID id = pedidos.usuario("MENTOR");
        mentores.add(id);
        return id;
    }

    /** Todo lo que MENTOR usa, menos {@code eventos} y sus hijas: lo que la consola ofrece marcar para esa base. */
    private List<String> todoMenosEventos() throws Exception {
        List<String> claves = new ArrayList<>();
        for (JsonNode funcionalidad : pedidos.json(pedidos.enviar("GET", "/api/v1/soporte-ti/funcionalidades",
                soporte, null, consola))) {
            boolean laUsaMentor = false;
            for (JsonNode rol : funcionalidad.get("rolesQueLaUsan")) {
                laUsaMentor |= rol.asText().equals("MENTOR");
            }
            String clave = funcionalidad.get("clave").asText();
            if (laUsaMentor && !clave.equals("eventos") && !clave.startsWith("eventos.")) {
                claves.add(clave);
            }
        }
        return claves;
    }

    private String crearMentorJunior() throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", "Mentor junior");
        cuerpo.put("rolBase", "MENTOR");
        cuerpo.put("funcionalidades", todoMenosEventos());
        cuerpo.put("motivo", "Mentores en su primer mes, sin eventos");
        HttpResponse<String> creado = pedidos.enviar("POST", RUTA, soporte, cuerpo, consola);
        assertThat(creado.statusCode()).isEqualTo(201);
        return pedidos.json(creado).get("id").asText();
    }

    private HttpResponse<String> eventosDe(String sesion) throws Exception {
        Instant desde = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return pedidos.enviar("GET", "/api/v1/calendar/events?from=" + desde + "&to=" + desde.plus(7, ChronoUnit.DAYS),
                sesion, null);
    }

    private List<String> noDisponiblesDe(String sesion) throws Exception {
        List<String> claves = new ArrayList<>();
        pedidos.json(pedidos.enviar("GET", "/api/v1/funcionalidades/mias", sesion, null)).get("noDisponibles")
                .forEach(clave -> claves.add(clave.asText()));
        return claves;
    }

    @Test
    @DisplayName("«Mentor junior» sin eventos: su mentor recibe 404 FUNCIONALIDAD_NO_DISPONIBLE y lo ve en noDisponibles; "
            + "otro mentor, 200; desactivarlo devuelve todo lo de la base")
    void mentorJuniorSinEventos() throws Exception {
        String rol = crearMentorJunior();
        UUID conRol = mentor();
        String sesionConRol = pedidos.sesionDe(conRol);
        String sesionSinRol = pedidos.sesionDe(mentor());
        assertThat(eventosDe(sesionConRol).statusCode()).as("antes de asignarlo").isEqualTo(200);

        assertThat(pedidos.enviar("PATCH", "/api/v1/users/" + conRol + "/role", admin,
                Map.of("rolPersonalizadoId", rol)).statusCode()).isEqualTo(204);

        HttpResponse<String> bloqueado = eventosDe(sesionConRol);
        assertThat(bloqueado.statusCode()).isEqualTo(404);
        assertThat(pedidos.json(bloqueado).get("codigo").asText()).isEqualTo("FUNCIONALIDAD_NO_DISPONIBLE");
        assertThat(pedidos.json(bloqueado).get("funcionalidad").asText()).isEqualTo("eventos");
        assertThat(noDisponiblesDe(sesionConRol)).containsExactlyInAnyOrder("eventos", "eventos.gestion",
                "eventos.asistencia");
        JsonNode mias = pedidos.json(pedidos.enviar("GET", "/api/v1/funcionalidades/mias", sesionConRol, null));
        assertThat(mias.get("apagadas")).as("no es mantenimiento").isEmpty();
        assertThat(pedidos.enviar("GET", "/api/v1/me/cells", sesionConRol, null).statusCode())
                .as("lo marcado sigue igual que para un mentor").isNotIn(404, 503);
        assertThat(eventosDe(sesionSinRol).statusCode()).as("otro mentor sin rol personalizado").isEqualTo(200);
        assertThat(noDisponiblesDe(sesionSinRol)).isEmpty();
        assertThat(esperar(() -> copiaDeOtraInstancia.foto().de(UserId.of(conRol)).isPresent()))
                .as("la otra instancia se enteró de la asignación por Redis").isTrue();

        Map<String, Object> desactivar = new LinkedHashMap<>();
        desactivar.put("nombre", "Mentor junior");
        desactivar.put("funcionalidades", todoMenosEventos());
        desactivar.put("activo", false);
        desactivar.put("motivo", "Terminó el primer mes");
        desactivar.put("version", 1);
        assertThat(pedidos.enviar("PUT", RUTA + "/" + rol, soporte, desactivar, consola).statusCode()).isEqualTo(200);

        assertThat(eventosDe(sesionConRol).statusCode()).as("desactivado: todo lo de la base").isEqualTo(200);
        assertThat(noDisponiblesDe(sesionConRol)).isEmpty();
        assertThat(pedidos.json(pedidos.enviar("GET", "/api/v1/auth/me", sesionConRol, null)).get("rolPersonalizado")
                .isNull()).isTrue();
        assertThat(esperar(() -> copiaDeOtraInstancia.foto().de(UserId.of(conRol)).isEmpty()))
                .as("y la otra instancia también").isTrue();
    }

    @Test
    @DisplayName("con personas no se borra (409); reemplazar en bloque a ninguno las libera (con contraseña) y entonces "
            + "se borra; queda auditado")
    void reemplazarYBorrar() throws Exception {
        String rol = crearMentorJunior();
        UUID uno = mentor();
        UUID dos = mentor();
        for (UUID mentor : List.of(uno, dos)) {
            assertThat(pedidos.enviar("PATCH", "/api/v1/users/" + mentor + "/role", admin,
                    Map.of("rolPersonalizadoId", rol)).statusCode()).isEqualTo(204);
        }
        assertThat(pedidos.json(pedidos.enviar("GET", RUTA, soporte, null, consola)).get(0).get("personas").asLong())
                .isEqualTo(2);

        assertThat(pedidos.enviar("DELETE", RUTA + "/" + rol + "?motivo=x", soporte, null, withReautenticacion())
                .statusCode()).as("con personas").isEqualTo(409);

        Map<String, Object> aNinguno = new LinkedHashMap<>();
        aNinguno.put("rolDestinoId", null);
        aNinguno.put("motivo", "Fin del piloto");
        HttpResponse<String> reemplazo = pedidos.enviar("POST", RUTA + "/" + rol + "/reemplazar", soporte, aNinguno,
                withReautenticacion());
        assertThat(reemplazo.statusCode()).isEqualTo(200);
        assertThat(pedidos.json(reemplazo).get("personasMovidas").asInt()).isEqualTo(2);
        assertThat(eventosDe(pedidos.sesionDe(uno)).statusCode()).as("quedó solo con la base").isEqualTo(200);

        assertThat(pedidos.enviar("DELETE", RUTA + "/" + rol + "?motivo=Ya%20no%20se%20usa", soporte, null,
                withReautenticacion()).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForList("SELECT tipo FROM renaser.cambios_auditados WHERE clave = ? ORDER BY creado_en",
                String.class, "rp:" + rol)).containsExactly("ROL_PERSONALIZADO", "ROL_PERSONALIZADO",
                "ROL_PERSONALIZADO");
    }

    private Map<String, String> withReautenticacion() throws Exception {
        return Map.of("X-Consola-Token", consola.get("X-Consola-Token"),
                "X-Reautenticacion", pedidos.reautenticar(soporte, consola.get("X-Consola-Token")));
    }

    private static boolean esperar(java.util.function.BooleanSupplier condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < limite) {
            if (condicion.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }
}
