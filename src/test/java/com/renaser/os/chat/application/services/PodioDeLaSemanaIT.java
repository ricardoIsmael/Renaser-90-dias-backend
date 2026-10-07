package com.renaser.os.chat.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase;
import com.renaser.os.chat.application.ports.in.ranking.PublicarPodioDeLaSemanaUseCase;
import com.renaser.os.chat.application.ports.in.ranking.PublicarPodioDeLaSemanaUseCase.Estado;
import com.renaser.os.chat.application.ports.in.ranking.PublicarPodioDeLaSemanaUseCase.ResultadoDelPodio;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.application.ports.out.ranking.DibujarPodioPort;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.ranking.SemanaDelRanking;
import com.renaser.os.notifications.application.ports.out.push.PushPort;
import com.renaser.os.points.api.RankingGeneralFinder;
import com.renaser.os.points.api.RankingGeneralFinder.PuestoEnElRankingGeneral;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * El podio semanal (D-262) contra Postgres de verdad: el padrón de {@code users}, los registros de {@code habits} y
 * el ranking general de {@code points} cerrado el domingo, y el mensaje del programa SIN persona en el grupo general
 * (V95). El almacenamiento de las pruebas es de marcador, así que sale solo el texto.
 *
 * <p>Reloj a las 03:00 UTC del martes 9 de marzo de 2027 = 22:00 del lunes 8 en Lima (regla 02 §3): la semana
 * cerrada es la del 1 al 7 de marzo. Una semana lejos de las demás pruebas, para que nadie más tenga hábitos en ella.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, PodioDeLaSemanaIT.RelojDelLunesEnLima.class})
class PodioDeLaSemanaIT {

    private static final Instant LUNES_8_A_LAS_22_DE_LIMA = Instant.parse("2027-03-09T03:00:00Z");
    private static final SemanaDelRanking SEMANA = new SemanaDelRanking(LocalDate.of(2027, 3, 1));

    @TestConfiguration
    static class RelojDelLunesEnLima {
        @Bean
        @Primary
        Clock relojDelLunesEnLima() {
            return FixedClock.at(LUNES_8_A_LAS_22_DE_LIMA);
        }
    }

    @MockitoBean
    private PushPort pushPort;
    @Autowired
    private PublicarPodioDeLaSemanaUseCase podioApagado;
    @Autowired
    private RankingGeneralFinder ranking;
    @Autowired
    private DibujarPodioPort dibujo;
    @Autowired
    private AlmacenamientoPort almacenamiento;
    @Autowired
    private LoadConversacionPort conversaciones;
    @Autowired
    private LoadMensajePort mensajes;
    @Autowired
    private EnviarMensajeDelProgramaUseCase delPrograma;
    @Autowired
    private UserSummaryFinder usuarios;
    @Autowired
    private Clock clock;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> personas = new ArrayList<>();
    private final List<UUID> habitos = new ArrayList<>();
    /** El grupo general lo crea la primera persona que entra ({@code ConversacionService.unirse}); si falta, se crea acá. */
    private UUID grupoGeneralCreadoAca;

    @BeforeEach
    void padron() {
        when(pushPort.enviar(anyList(), any())).thenReturn(List.of());
        if (conversaciones.global().isEmpty()) {
            grupoGeneralCreadoAca = UUID.randomUUID();
            jdbc.update("INSERT INTO renaser.conversaciones (id, tipo, nombre) VALUES (?, 'GLOBAL', 'Comunidad Global')",
                    grupoGeneralCreadoAca);
        }
        aprendizConLaSemanaCompleta("Zoila Quispe Huamán");
    }

    @AfterEach
    void limpiar() throws InterruptedException {
        esperarQueElOutboxTermine();
        jdbc.update("DELETE FROM renaser.mensajes WHERE id IN (?, ?)", SEMANA.idDelTexto().value(),
                SEMANA.idDeLaImagen().value());
        personas.forEach(id -> jdbc.update("DELETE FROM renaser.notificaciones WHERE usuario_id = ?", id));
        personas.forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        habitos.forEach(id -> jdbc.update("DELETE FROM renaser.habitos WHERE id = ?", id));
        if (grupoGeneralCreadoAca != null) {
            jdbc.update("DELETE FROM renaser.conversaciones WHERE id = ?", grupoGeneralCreadoAca);
        }
    }

    @Test
    @DisplayName("prendido: publica UNA vez aunque corra dos, sin emisor, firmado por el programa, con el ranking al domingo")
    void publicaUnaVez() {
        PublicarPodioDeLaSemanaUseCase podio = servicio(ranking, true);

        ResultadoDelPodio primera = podio.publicarLaSemanaCerrada();
        ResultadoDelPodio segunda = podio.publicarLaSemanaCerrada();

        assertThat(primera.lunes()).isEqualTo(SEMANA.lunes());
        assertThat(primera.estado()).isEqualTo(Estado.PUBLICADO);
        assertThat(primera.piezas()).as("solo el texto: el almacenamiento de las pruebas es de marcador").isEqualTo(1);
        assertThat(segunda.estado()).isEqualTo(Estado.YA_ESTABA);
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT m.tipo::text AS tipo, m.emisor_id, m.texto, c.tipo AS chat FROM renaser.mensajes m
                JOIN renaser.conversaciones c ON c.id = m.conversacion_id WHERE m.id IN (?, ?)""",
                SEMANA.idDelTexto().value(), SEMANA.idDeLaImagen().value());
        assertThat(filas).singleElement().satisfies(fila -> {
            assertThat(fila.get("tipo")).isEqualTo("SISTEMA");
            assertThat(fila.get("emisor_id")).as("sin persona (V95)").isNull();
            assertThat(fila.get("chat")).isEqualTo("GLOBAL");
            assertThat((String) fila.get("texto")).contains("Zoila Q.");
        });
        Mensaje leido = mensajes.porId(SEMANA.idDelTexto()).orElseThrow();
        assertThat(leido.emisorId()).as("el adaptador JPA lee la fila sin emisor").isNull();
        assertThat(leido.remitentePublico()).isEqualTo(Mensaje.ID_PUBLICO_DEL_PROGRAMA);
    }

    @Test
    @DisplayName("apagado (el default): no publica nada")
    void apagadoNoPublica() {
        assertThat(podioApagado.publicarLaSemanaCerrada().estado()).isEqualTo(Estado.APAGADO);
        assertThat(cuantosDeLaSemana()).isZero();
    }

    @Test
    @DisplayName("si nadie tiene puntaje, no publica")
    void sinPuntajesNoPublica() {
        RankingGeneralFinder todosEnCero = hasta -> List.of(
                new PuestoEnElRankingGeneral(UserId.of(personas.get(0)), "Zoila Quispe", BigDecimal.ZERO));

        assertThat(servicio(todosEnCero, true).publicarLaSemanaCerrada().estado()).isEqualTo(Estado.SIN_PUNTAJES);
        assertThat(cuantosDeLaSemana()).isZero();
    }

    @Test
    @DisplayName("V95: la base solo acepta un mensaje sin emisor si es del programa")
    void sinEmisorSoloDelPrograma() {
        UUID global = conversaciones.global().orElseThrow().id().value();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO renaser.mensajes (id, conversacion_id, emisor_id, tipo, texto)
                VALUES (?, ?, NULL, 'TEXTO', 'hola')""", UUID.randomUUID(), global))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("mensaje_sin_emisor_solo_del_programa");
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    private PodioDeLaSemanaService servicio(RankingGeneralFinder finder, boolean activo) {
        return new PodioDeLaSemanaService(finder, dibujo, almacenamiento, conversaciones, mensajes, delPrograma, usuarios,
                clock, activo);
    }

    private int cuantosDeLaSemana() {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.mensajes WHERE id IN (?, ?)", Integer.class,
                SEMANA.idDelTexto().value(), SEMANA.idDeLaImagen().value());
    }

    /** Arrancó el sábado 27 de febrero (Día 10 el lunes 8); del 1 al 7 de marzo cumplió todo lo de cada día. */
    private void aprendizConLaSemanaCompleta(String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, 'APRENDIZ', 'ACTIVO')""", id, id + "@renaser.test", nombre);
        personas.add(id);
        jdbc.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, programa_activado_en,
                                                            timezone)
                VALUES (?, 10, ?, ?, 'America/Lima')""", id, LocalDate.of(2027, 2, 27),
                Timestamp.from(Instant.parse("2027-02-26T15:00:00Z")));
        for (LocalDate dia = SEMANA.lunes(); !dia.isAfter(SEMANA.domingo()); dia = dia.plusDays(1)) {
            registroCumplido(id, dia, (int) (dia.toEpochDay() - LocalDate.of(2027, 2, 27).toEpochDay()) + 1);
        }
    }

    private void registroCumplido(UUID participante, LocalDate fecha, int diaPrograma) {
        UUID habito = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.habitos (id, titulo, categoria_clave)
                VALUES (?, ?, (SELECT clave FROM renaser.categorias_habito LIMIT 1))""", habito, "Hábito " + habito);
        habitos.add(habito);
        jdbc.update("""
                INSERT INTO renaser.registros_habito (id, participante_id, habito_id, fecha_ejecucion, dia_programa,
                                                     tipo_dia, es_opcional, estado)
                VALUES (?, ?, ?, ?, ?, CAST('DISCIPLINA' AS renaser.tipo_dia), false, CAST('COMPLETADO' AS renaser.estado_registro))
                """, UUID.randomUUID(), participante, habito, fecha, diaPrograma);
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
