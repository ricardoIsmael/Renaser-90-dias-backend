package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.chat.application.ports.in.conversacion.ListarConversacionesUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.ListarConversacionesUseCase.ConversacionResumen;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.EnviarMensajeCommand;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.OrigenMedia;
import com.renaser.os.chat.application.ports.out.presencia.ConversacionAbiertaPort;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.notifications.application.ports.out.push.MensajePush;
import com.renaser.os.notifications.application.ports.out.push.PushPort;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.notifications.infrastructure.adapter.in.scheduler.PurgaNotificacionesScheduler;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D-221 de punta a punta contra Postgres y Redis de verdad: se guarda un mensaje en un grupo, el
 * evento pasa por el outbox, {@code notifications} pregunta a {@code chat} a quién avisar y emite por
 * el camino de siempre. El push se captura ({@link PushPort} doble): lo demás es el código real.
 *
 * <p>Lo que un doble no puede probar: la regla de acceso de verdad (pertenencia vigente en
 * {@code asignaciones_celula}, no la proyección), la preferencia guardada, el conteo de no leídos
 * en SQL, la llave de Redis del chat abierto y el nombre derivado del mentor de HOY.
 *
 * <p>Sin {@code @Transactional}: el listener corre después del commit, en otro hilo.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MensajeDeChatAvisoIT {

    @MockitoBean
    private PushPort pushPort;

    @Autowired
    private EnviarMensajeUseCase enviarMensaje;
    @Autowired
    private ListarConversacionesUseCase listarConversaciones;
    @Autowired
    private ConversacionAbiertaPort conversacionAbierta;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PurgaNotificacionesScheduler purga;
    @Autowired
    private DataSource dataSource;

    private UUID cohorteId;
    private UUID grupo;
    private UUID conversacion;
    private final List<UUID> usuarios = new ArrayList<>();
    private final Map<String, UUID> gente = new HashMap<>();
    private final Map<String, UUID> asignaciones = new HashMap<>();

    @BeforeEach
    void semilla() {
        when(pushPort.enviar(anyList(), any())).thenReturn(List.of());
        cohorteId = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, 'Cohorte avisos', CURRENT_DATE - 10)",
                cohorteId);
        grupo = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin)
                VALUES (?, 'Fenix', ?, 'REGULAR', CURRENT_DATE - 5, CURRENT_DATE + 20)
                """, grupo, cohorteId);
        conversacion = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.conversaciones (id, tipo, celula_id) VALUES (?, 'CELULA', ?)", conversacion, grupo);

        persona("luisa", "MENTOR", "Luisa Fernanda Quispe", "ACTIVO", "MENTOR");
        persona("ana", "APRENDIZ", "Ana Pérez", "ACTIVO", "APRENDIZ");
        persona("beto", "APRENDIZ", "Beto Ruiz", "ACTIVO", "APRENDIZ");
        persona("caro", "APRENDIZ", "Caro Díaz", "SUSPENDIDO", "APRENDIZ");
        persona("dani", "APRENDIZ", "Dani Soto", "ACTIVO", "APRENDIZ");
        // Pedro fue el mentor antes (un grupo tiene un solo mentor a la vez): su asignación está cerrada,
        // pero su fila en participantes_conversacion quedó. Es la proyección vieja que concede de más.
        persona("pedro", "MENTOR", "Pedro Ex", "ACTIVO", null);
        asignarEntre("pedro", "MENTOR", "now() - interval '3 days'", "now() - interval '2 days'");
        jdbc.update("INSERT INTO renaser.participantes_conversacion (conversacion_id, usuario_id) VALUES (?, ?)",
                conversacion, gente.get("pedro"));
        // Zoe no está en el grupo ni en el chat.
        persona("zoe", "APRENDIZ", "Zoe Afuera", "ACTIVO", null);
        jdbc.update("INSERT INTO renaser.preferencias_notificacion (usuario_id, tipo, habilitada) VALUES (?, 'MENSAJE_CHAT', false)",
                gente.get("dani"));
    }

    @AfterEach
    void limpiar() {
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.notificaciones WHERE usuario_id = ?", id));
        jdbc.update("DELETE FROM renaser.mensajes WHERE conversacion_id = ?", conversacion);
        jdbc.update("DELETE FROM renaser.conversaciones WHERE id = ?", conversacion);
        jdbc.update("DELETE FROM renaser.celulas WHERE id = ?", grupo);
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        jdbc.update("DELETE FROM renaser.politicas_mentoria WHERE cohorte_id = ?", cohorteId);
        jdbc.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorteId);
    }

    @Test
    @DisplayName("un mensaje en el grupo avisa a los integrantes de HOY menos el autor; preferencia apagada y ex mentor sin aviso; suspendida con fila y sin push")
    void quienRecibeElAviso() throws InterruptedException {
        Mensaje mensaje = escribe("ana", "hola a todos");
        esperarQueElOutboxTermine();

        assertThat(conAviso(mensaje)).containsExactlyInAnyOrder(gente.get("luisa"), gente.get("beto"), gente.get("caro"));
        List<UUID> conPush = destinatariosDelPush();
        assertThat(conPush).as("con push").containsExactlyInAnyOrder(gente.get("luisa"), gente.get("beto"));
        assertThat(conPush).as("la autora no se avisa a sí misma, ni la suspendida, ni quien lo apagó, ni el ex mentor, ni alguien de afuera")
                .doesNotContain(gente.get("ana"), gente.get("caro"), gente.get("dani"), gente.get("pedro"), gente.get("zoe"));

        MensajePush push = pushDe("beto");
        assertThat(push.titulo()).isEqualTo("Luisa y sus aprendices");
        assertThat(push.cuerpo()).isEqualTo("Ana Pérez: hola a todos");
        assertThat(push.rutaApp()).isEqualTo("/chat/" + conversacion);
        assertThat(push.etiqueta()).contains("chat-" + conversacion);
    }

    @Test
    @DisplayName("el segundo sin leer lleva el conteo en el título; quien tiene el chat abierto no recibe push ni fila")
    void conteoYChatAbierto() throws InterruptedException {
        escribe("ana", "uno");
        esperarQueElOutboxTermine();
        clearInvocations(pushPort);

        conversacionAbierta.marcarAbierta(UserId.of(gente.get("luisa")), ConversacionId.of(conversacion), Duration.ofMinutes(1));
        try {
            Mensaje segundo = escribe("ana", "dos");
            esperarQueElOutboxTermine();

            assertThat(pushDe("beto").titulo()).isEqualTo("Luisa y sus aprendices (2 mensajes nuevos)");
            assertThat(conAviso(segundo)).as("Luisa lo está viendo").doesNotContain(gente.get("luisa"));
            assertThat(destinatariosDelPush()).doesNotContain(gente.get("luisa"));
        } finally {
            conversacionAbierta.marcarCerrada(UserId.of(gente.get("luisa")), ConversacionId.of(conversacion));
        }
    }

    @Test
    @DisplayName("el nombre del grupo se deriva del mentor de HOY: rota y cambia; sin mentor, el nombre del grupo")
    void nombreDelGrupoDerivado() {
        assertThat(nombreDelChatPara("beto")).isEqualTo("Luisa y sus aprendices");

        jdbc.update("UPDATE renaser.asignaciones_celula SET fin = now() - interval '1 minute' WHERE id = ?",
                asignaciones.get("luisa"));
        assertThat(nombreDelChatPara("beto")).as("sin mentor vigente").isEqualTo("Fenix");

        asignarEntre("pedro", "MENTOR", "now()", null);
        assertThat(nombreDelChatPara("beto")).as("el mentor nuevo").isEqualTo("Pedro y sus aprendices");
    }

    @Test
    @DisplayName("el soporte se nombra con el primer nombre del aprendiz aunque se haya creado con el formato viejo")
    void nombreDelSoporteDerivado() {
        UUID soporte = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.conversaciones (id, tipo, clave_directa, nombre) VALUES (?, 'SOPORTE', ?, 'Soporte - Beto Ruiz')",
                soporte, "soporte:" + gente.get("beto"));
        jdbc.update("INSERT INTO renaser.participantes_conversacion (conversacion_id, usuario_id) VALUES (?, ?)",
                soporte, gente.get("beto"));
        try {
            String nombre = listarConversaciones.listar(UserId.of(gente.get("beto"))).stream()
                    .filter(r -> r.conversacion().id().value().equals(soporte))
                    .map(ConversacionResumen::nombre).findFirst().orElseThrow();
            assertThat(nombre).isEqualTo("Beto – Formación Renaser");
        } finally {
            jdbc.update("DELETE FROM renaser.conversaciones WHERE id = ?", soporte);
        }
    }

    @Test
    @DisplayName("la purga de la noche (sin transacción alrededor, como la llama el cron) borra los avisos de chat de más de 7 días")
    void purgaDeLosAvisosDeChat() {
        UUID viejo = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.notificaciones (usuario_id, tipo, titulo, cuerpo, creado_en, origen_evento_id)
                VALUES (?, 'MENSAJE_CHAT', 'Chat', 'Ana: hola', now() - interval '8 days', ?)
                """, gente.get("beto"), viejo);

        purga.purgarAntiguas();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.notificaciones WHERE origen_evento_id = ?",
                Integer.class, viejo)).isZero();
    }

    @Test
    @DisplayName("V83 renombra la comunidad existente a «Formación Renaser Global»")
    void laMigracionRenombraLaComunidad() {
        HikariDataSource principal = (HikariDataSource) dataSource;
        String base = "renaser_v83_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE " + base);
        try {
            String url = principal.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + base + "$1");
            DriverManagerDataSource aparte = new DriverManagerDataSource(url, principal.getUsername(), principal.getPassword());
            Flyway.configure().dataSource(aparte).locations("classpath:db/migration").target("82").load().migrate();
            JdbcTemplate otra = new JdbcTemplate(aparte);
            otra.update("INSERT INTO renaser.conversaciones (tipo, nombre) VALUES ('GLOBAL', 'Comunidad Global')");

            Flyway.configure().dataSource(aparte).locations("classpath:db/migration").target("83").load().migrate();

            assertThat(otra.queryForObject("SELECT nombre FROM renaser.conversaciones WHERE tipo = 'GLOBAL'", String.class))
                    .isEqualTo("Formación Renaser Global");
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS " + base + " WITH (FORCE)");
        }
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    private Mensaje escribe(String quien, String texto) {
        return enviarMensaje.enviar(new EnviarMensajeCommand(UserId.of(gente.get(quien)), ConversacionId.of(conversacion),
                TipoMensaje.TEXTO, texto, null, null, null, null, null, null, OrigenMedia.CLIENTE)).mensaje();
    }

    private String nombreDelChatPara(String quien) {
        return listarConversaciones.listar(UserId.of(gente.get(quien))).stream()
                .filter(r -> r.conversacion().id().value().equals(conversacion))
                .map(ConversacionResumen::nombre).findFirst().orElseThrow();
    }

    private List<UUID> conAviso(Mensaje mensaje) {
        return jdbc.queryForList("SELECT usuario_id FROM renaser.notificaciones WHERE tipo = 'MENSAJE_CHAT' AND origen_evento_id = ?",
                UUID.class, mensaje.id().value());
    }

    @SuppressWarnings("unchecked")
    private List<UUID> destinatariosDelPush() {
        ArgumentCaptor<List<TokenPush>> tokens = ArgumentCaptor.forClass(List.class);
        verify(pushPort, atLeast(0)).enviar(tokens.capture(), any());
        return tokens.getAllValues().stream().flatMap(List::stream).map(t -> t.usuarioId().value()).toList();
    }

    @SuppressWarnings("unchecked")
    private MensajePush pushDe(String quien) {
        ArgumentCaptor<List<TokenPush>> tokens = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<MensajePush> mensajes = ArgumentCaptor.forClass(MensajePush.class);
        verify(pushPort, atLeast(1)).enviar(tokens.capture(), mensajes.capture());
        MensajePush ultimo = null;
        for (int i = 0; i < tokens.getAllValues().size(); i++) {
            if (tokens.getAllValues().get(i).stream().anyMatch(t -> t.usuarioId().value().equals(gente.get(quien)))) {
                ultimo = mensajes.getAllValues().get(i);
            }
        }
        assertThat(ultimo).as("push a " + quien).isNotNull();
        return ultimo;
    }

    private void persona(String clave, String rol, String nombre, String estado, String funcion) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), CAST(? AS renaser.estado_usuario))
                """, id, id + "@renaser.test", nombre, rol, estado);
        usuarios.add(id);
        gente.put(clave, id);
        jdbc.update("INSERT INTO renaser.tokens_push (usuario_id, token, plataforma) VALUES (?, ?, 'ANDROID')",
                id, "ExponentPushToken[" + id + "]");
        if (funcion != null) {
            asignar(clave, funcion);
            jdbc.update("INSERT INTO renaser.participantes_conversacion (conversacion_id, usuario_id) VALUES (?, ?)",
                    conversacion, id);
        }
    }

    private void asignar(String clave, String funcion) {
        asignarEntre(clave, funcion, "now() - interval '1 hour'", null);
    }

    /** {@code inicio} y {@code fin} son expresiones SQL fijas de esta prueba ({@code fin} null = abierta). */
    private void asignarEntre(String clave, String funcion, String inicio, String fin) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.asignaciones_celula (id, celula_id, usuario_id, funcion, inicio, fin, motivo, clave_operacion) "
                + "VALUES (?, ?, ?, CAST(? AS renaser.funcion_acompanamiento), " + inicio + ", " + (fin == null ? "NULL" : fin)
                + ", 'ADMINISTRATIVO', ?)", id, grupo, gente.get(clave), funcion, "prueba|" + id);
        asignaciones.put(clave, id);
    }

    private void esperarQueElOutboxTermine() throws InterruptedException {
        long limite = System.currentTimeMillis() + 20_000;
        while (pendientesEnElOutbox() > 0 && System.currentTimeMillis() < limite) {
            Thread.sleep(200);
        }
        assertThat(pendientesEnElOutbox()).as("publicaciones sin completar en el outbox").isZero();
    }

    private int pendientesEnElOutbox() {
        return jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE completion_date IS NULL", Integer.class);
    }
}
