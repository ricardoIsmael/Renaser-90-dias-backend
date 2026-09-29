package com.renaser.os.chat.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.chat.application.ports.in.semaforo.EnviarTarjetasDelSemaforoUseCase;
import com.renaser.os.chat.application.ports.in.semaforo.EnviarTarjetasDelSemaforoUseCase.ResultadoDeTarjetas;
import com.renaser.os.notifications.application.ports.out.push.PushPort;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La tarjeta diaria del semáforo (D-223) de punta a punta contra Postgres y Redis de verdad: el padrón de
 * {@code users}, los registros de {@code habits} leídos por {@code points}, el mensaje del programa en el
 * soporte y el push por el outbox. El almacenamiento de las pruebas es de marcador, así que sale solo el texto.
 *
 * <p>Reloj a las 04:52 UTC del 29: en Lima son las 23:52 del 28 (regla 02 §3). Ana tiene registros el 28
 * (7 de 8, 88 % → verde) y el 29 (0 de 2): si el barrido usara la fecha del servidor, diría 0 %.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TarjetaDelSemaforoIT.RelojDeLas2352DeLima.class})
class TarjetaDelSemaforoIT {

    private static final Instant LAS_2352_DEL_28_EN_LIMA = Instant.parse("2026-09-29T04:52:00Z");
    private static final LocalDate DIA_28 = LocalDate.of(2026, 9, 28);
    private static final LocalDate DIA_29 = LocalDate.of(2026, 9, 29);

    @TestConfiguration
    static class RelojDeLas2352DeLima {
        @Bean
        @Primary
        Clock relojDeLas2352DeLima() {
            return FixedClock.at(LAS_2352_DEL_28_EN_LIMA);
        }
    }

    @MockitoBean
    private PushPort pushPort;
    @Autowired
    private EnviarTarjetasDelSemaforoUseCase tarjetas;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> soportes = new ArrayList<>();
    private UUID ana;
    private UUID beto;
    private UUID admin;
    private UUID soporteDeAna;
    private UUID soporteDeBeto;

    @BeforeEach
    void semilla() {
        when(pushPort.enviar(anyList(), any())).thenReturn(List.of());
        admin = persona("ADMIN", "ACTIVO", "Kelin Admin");
        ana = aprendizConDia("ACTIVO", "Ana Pérez");
        beto = aprendizConDia("SUSPENDIDO", "Beto Ruiz");
        soporteDeAna = soporte(ana);
        soporteDeBeto = soporte(beto);
    }

    @AfterEach
    void limpiar() {
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.notificaciones WHERE usuario_id = ?", id));
        soportes.forEach(id -> jdbc.update("DELETE FROM renaser.conversaciones WHERE id = ?", id));
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    @Test
    @DisplayName("sale una vez con el % del día de LIMA; la segunda corrida no duplica; noop manda solo texto; suspendido no; push solo a la aprendiz")
    void laTarjetaDeLaNoche() throws InterruptedException {
        ResultadoDeTarjetas primera = tarjetas.enviarLasQueTocan();
        ResultadoDeTarjetas segunda = tarjetas.enviarLasQueTocan();
        esperarQueElOutboxTermine();

        List<Map<String, Object>> deAna = jdbc.queryForList("""
                SELECT tipo::text AS tipo, emisor_id, texto, media_ruta FROM renaser.mensajes
                WHERE conversacion_id = ? ORDER BY creado_en""", soporteDeAna);
        assertThat(deAna).as("una sola, sin imagen (noop), del programa a nombre de Ana").singleElement().satisfies(m -> {
            assertThat(m.get("tipo")).isEqualTo("SISTEMA");
            assertThat(m.get("emisor_id")).isEqualTo(ana);
            assertThat(m.get("texto")).isEqualTo("Hoy llevas 88 % de tus hábitos.");
            assertThat(m.get("media_ruta")).isNull();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.mensajes WHERE conversacion_id = ?", Integer.class,
                soporteDeBeto)).as("cuenta suspendida").isZero();
        // La base de este contexto puede traer a otros aprendices: se mira que la de Ana saliera y que la
        // segunda corrida no mande nada a nadie.
        assertThat(primera.enviadas()).isGreaterThanOrEqualTo(1);
        assertThat(segunda.enviadas()).isZero();

        assertThat(destinatariosDelPush()).as("el staff del soporte no recibe push de la tarjeta")
                .containsExactly(ana);
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    private UUID persona(String rol, String estado, String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), CAST(? AS renaser.estado_usuario))
                """, id, id + "@renaser.test", nombre, rol, estado);
        jdbc.update("INSERT INTO renaser.tokens_push (usuario_id, token, plataforma) VALUES (?, ?, 'ANDROID')",
                id, "ExponentPushToken[" + id + "]");
        usuarios.add(id);
        return id;
    }

    /** Día 9 del programa el 28 (arrancó el 20), en Lima. El 28: 7 de 8; el 29: 0 de 2. */
    private UUID aprendizConDia(String estado, String nombre) {
        UUID id = persona("APRENDIZ", estado, nombre);
        jdbc.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio,
                                                            programa_activado_en, timezone)
                VALUES (?, 9, ?, ?, 'America/Lima')
                """, id, LocalDate.of(2026, 9, 20), Timestamp.from(Instant.parse("2026-09-19T15:00:00Z")));
        for (int i = 0; i < 8; i++) {
            registro(id, DIA_28, 9, i < 7 ? "COMPLETADO" : "EXPIRADO");
        }
        registro(id, DIA_29, 10, "PENDIENTE");
        registro(id, DIA_29, 10, "PENDIENTE");
        return id;
    }

    private void registro(UUID participante, LocalDate fecha, int diaPrograma, String estado) {
        UUID habito = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.habitos (id, titulo, categoria_clave)
                VALUES (?, ?, (SELECT clave FROM renaser.categorias_habito LIMIT 1))
                """, habito, "Hábito " + habito);
        jdbc.update("""
                INSERT INTO renaser.registros_habito (id, participante_id, habito_id, fecha_ejecucion, dia_programa,
                                                     tipo_dia, es_opcional, estado)
                VALUES (?, ?, ?, ?, ?, CAST('DISCIPLINA' AS renaser.tipo_dia), false, CAST(? AS renaser.estado_registro))
                """, UUID.randomUUID(), participante, habito, fecha, diaPrograma, estado);
    }

    /** El soporte de un aprendiz, con él y el Admin adentro, como lo arma {@code ConversacionSoporteService}. */
    private UUID soporte(UUID aprendiz) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.conversaciones (id, tipo, clave_directa, nombre) VALUES (?, 'SOPORTE', ?, 'Soporte')",
                id, "soporte:" + aprendiz);
        for (UUID quien : List.of(aprendiz, admin)) {
            jdbc.update("INSERT INTO renaser.participantes_conversacion (conversacion_id, usuario_id) VALUES (?, ?)", id, quien);
        }
        soportes.add(id);
        return id;
    }

    @SuppressWarnings("unchecked")
    private List<UUID> destinatariosDelPush() {
        ArgumentCaptor<List<TokenPush>> tokens = ArgumentCaptor.forClass(List.class);
        verify(pushPort, atLeast(0)).enviar(tokens.capture(), any());
        return tokens.getAllValues().stream().flatMap(List::stream).map(t -> t.usuarioId().value())
                .filter(usuarios::contains).toList();
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
