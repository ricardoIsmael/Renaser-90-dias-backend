package com.renaser.os.plataforma;

import com.renaser.os.TestcontainersConfiguration;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El push a Administración cuando Soporte TI cambia un rol personalizado con personas (spec 002, US7 escenario 6;
 * D-269; decisión del dueño del 2026-10-14), de punta a punta: la consola publica el aviso por el outbox y
 * {@code notifications} deja una fila {@code OPERACION_PLATAFORMA} (con su push) a cada ADMIN y ALQUIMISTA activo, a
 * nadie más, y nunca a una cuenta de prueba de «ver como».
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AvisoDeRolConPersonasIT {

    private static final String RUTA = "/api/v1/soporte-ti/roles";

    @LocalServerPort
    private int puerto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private PedidosDePrueba pedidos;
    private String soporte;
    private UUID soporteId;
    private Map<String, String> consola;
    private UUID admin;
    private UUID alquimista;
    private UUID adminDePrueba;
    private final Instant inicio = Instant.now();

    @BeforeEach
    void armar() throws Exception {
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
        soporteId = pedidos.usuario("SOPORTE_TI");
        soporte = pedidos.sesionDe(soporteId);
        consola = Map.of("X-Consola-Token", pedidos.desbloquear(soporte));
        admin = pedidos.usuario("ADMIN");
        alquimista = pedidos.usuario("ALQUIMISTA");
        adminDePrueba = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado, es_cuenta_de_prueba)
                VALUES (?, ?, 'Prueba · Administrador', 'ADMIN', 'ACTIVO', true)
                """, adminDePrueba, "ver-como+it-" + adminDePrueba + "@renaser.invalid");
    }

    @AfterEach
    void limpiar() throws InterruptedException {
        pedidos.esperarElOutbox(inicio);
        jdbc.update("UPDATE renaser.usuarios SET rol_personalizado_id = NULL WHERE rol_personalizado_id IS NOT NULL");
        jdbc.update("DELETE FROM renaser.roles_personalizados");
        jdbc.update("DELETE FROM renaser.historial_rol_participante WHERE rige_desde >= ?", Timestamp.from(inicio));
        jdbc.update("DELETE FROM renaser.notificaciones WHERE usuario_id = ?", adminDePrueba);
        jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", adminDePrueba);
        pedidos.borrarCuentas();
    }

    private List<Map<String, Object>> avisosDe(UUID usuario) {
        return jdbc.queryForList("SELECT titulo, cuerpo, ruta_app FROM renaser.notificaciones WHERE usuario_id = ? "
                + "AND tipo = 'OPERACION_PLATAFORMA' AND creado_en >= ? ORDER BY creado_en", usuario,
                Timestamp.from(inicio));
    }

    private Map<String, Object> edicion(String nombre, boolean activo, long version, String motivo) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("funcionalidades", List.of("muro"));
        cuerpo.put("activo", activo);
        cuerpo.put("motivo", motivo);
        cuerpo.put("version", version);
        return cuerpo;
    }

    @Test
    @DisplayName("desactivar un rol con 2 personas avisa a cada ADMIN y ALQUIMISTA, no a la cuenta de prueba ni a los "
            + "mentores; renombrarlo no avisa; el reemplazo en bloque que las mueve, sí")
    void avisaAAdministracion() throws Exception {
        Map<String, Object> nuevo = new LinkedHashMap<>();
        nuevo.put("nombre", "Mentor junior");
        nuevo.put("rolBase", "MENTOR");
        nuevo.put("funcionalidades", List.of("muro"));
        nuevo.put("motivo", "Piloto");
        String rol = pedidos.json(pedidos.enviar("POST", RUTA, soporte, nuevo, consola)).get("id").asText();
        String sesionAdmin = pedidos.sesionDe(admin);
        UUID mentor = pedidos.usuario("MENTOR");
        for (UUID cuenta : List.of(mentor, pedidos.usuario("MENTOR"))) {
            assertThat(pedidos.enviar("PATCH", "/api/v1/users/" + cuenta + "/role", sesionAdmin,
                    Map.of("rolPersonalizadoId", rol)).statusCode()).isEqualTo(204);
        }
        pedidos.esperarElOutbox(inicio);
        assertThat(avisosDe(admin)).as("crear y asignar no avisan").isEmpty();

        assertThat(pedidos.enviar("PUT", RUTA + "/" + rol, soporte, edicion("Mentor junior", false, 1,
                "Terminó el piloto"), consola).statusCode()).isEqualTo(200);
        pedidos.esperarElOutbox(inicio);

        String esperado = "Soporte TI cambió el rol «Mentor junior» (2 personas): lo desactivó: quedan solo con su "
                + "rol base (Mentor). Motivo: Terminó el piloto";
        for (UUID quien : List.of(admin, alquimista)) {
            assertThat(avisosDe(quien)).singleElement().satisfies(aviso -> {
                assertThat(aviso.get("titulo")).isEqualTo("Soporte TI cambió un rol");
                assertThat(aviso.get("cuerpo")).isEqualTo(esperado);
                assertThat(aviso.get("ruta_app")).isNull();
            });
        }
        assertThat(avisosDe(adminDePrueba)).as("la cuenta de prueba nunca recibe avisos").isEmpty();
        assertThat(avisosDe(mentor)).isEmpty();
        assertThat(avisosDe(soporteId)).isEmpty();

        assertThat(pedidos.enviar("PUT", RUTA + "/" + rol, soporte, edicion("Mentor inicial", false, 2,
                "Renombrarlo"), consola).statusCode()).isEqualTo(200);
        pedidos.esperarElOutbox(inicio);
        assertThat(avisosDe(admin)).as("renombrar no cambia nada para nadie").hasSize(1);

        Map<String, Object> aNinguno = new HashMap<>();
        aNinguno.put("rolDestinoId", null);
        aNinguno.put("motivo", "Fin del piloto");
        assertThat(pedidos.enviar("POST", RUTA + "/" + rol + "/reemplazar", soporte, aNinguno,
                Map.of("X-Consola-Token", consola.get("X-Consola-Token"),
                        "X-Reautenticacion", pedidos.reautenticar(soporte, consola.get("X-Consola-Token"))))
                .statusCode()).isEqualTo(200);
        pedidos.esperarElOutbox(inicio);

        assertThat(avisosDe(alquimista)).hasSize(2).last().satisfies(aviso -> assertThat(aviso.get("cuerpo"))
                .isEqualTo("Soporte TI cambió el rol «Mentor inicial» (2 personas): quedaron solo con su rol base "
                        + "(Mentor). Motivo: Fin del piloto"));
        assertThat(avisosDe(adminDePrueba)).isEmpty();
    }
}
