package com.renaser.os.leadership;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.habits.api.CodigoRenaserDePersonaFinder;
import com.renaser.os.habits.api.CodigoRenaserDePersonaFinder.CodigoRenaserDePersona;
import com.renaser.os.habits.api.CodigoRenaserDePersonaFinder.RegistroDelRadar;
import com.renaser.os.leadership.application.ports.in.ConsultarGrupoDelLiderUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarGrupoDelLiderUseCase.ConsultaGrupoDelLider;
import com.renaser.os.leadership.application.ports.in.ConsultarRadarDePersonaUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarRadarDePersonaUseCase.ConsultaRadarDePersona;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.ConsultaSemanaDePersona;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.DiaDeLaFicha;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.SemanaDeLaPersona;
import com.renaser.os.leadership.domain.model.cumplimiento.ColorDeLaFila;
import com.renaser.os.leadership.domain.model.cumplimiento.PersonaDeLaLista;
import com.renaser.os.mentoring.api.ObligacionDelDia;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DetalleDelSemaforo;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La ficha del líder contra Postgres real (spec 005): lo que solo se ve con la base de verdad. Las consultas nuevas
 * (respuesta escrita y evidencias de cada obligación, publicación del post diario, acciones del día con su foto), el
 * registro de cada consulta y el trigger que lo hace append-only.
 *
 * <p>Se doblan solo las fronteras que este módulo no controla: el reloj (lunes 10:00 en Lima), el grupo de
 * {@code community} y el semáforo de {@code points}. Todo lo demás —habits, evidence, community (Muro), rocks, users— es
 * lo real.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, CumplimientoDelLiderIT.Dobles.class})
class CumplimientoDelLiderIT {

    private static final Instant AHORA = Instant.parse("2026-10-19T15:00:00Z");
    private static final LocalDate DESDE = LocalDate.of(2026, 10, 10);
    private static final LocalDate VIERNES = LocalDate.of(2026, 10, 16);
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 10, 14);
    private static final UUID FENIX = UUID.randomUUID();
    private static final UUID LIDER = UUID.randomUUID();
    private static final UUID MENTORA = UUID.randomUUID();
    private static final UUID ANA = UUID.randomUUID();

    @Autowired
    private ConsultarGrupoDelLiderUseCase grupo;
    @Autowired
    private ConsultarSemanaDePersonaUseCase semana;
    @Autowired
    private ConsultarRadarDePersonaUseCase radar;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID ritual;
    private UUID postDiario;
    private UUID publicacion;
    private UUID accion;
    private UUID fotoDelRitual;
    private UUID fotoDeLaAccion;

    @BeforeEach
    void sembrar() {
        jdbc.update("DELETE FROM renaser.usuarios WHERE id IN (?, ?, ?)", LIDER, MENTORA, ANA);
        usuario(LIDER, "LIDER_MENTORES", "Líder Uno");
        usuario(MENTORA, "MENTOR", "Luisa Rojas");
        usuario(ANA, "APRENDIZ", "Ana Pérez");
        programa(MENTORA);
        programa(ANA);
        UUID habitoRitual = nuevoHabito("Ritual de la mañana " + UUID.randomUUID());
        UUID habitoPost = jdbc.queryForObject(
                "SELECT id FROM renaser.habitos WHERE clave_sistema = 'COMMUNITY_POST'", UUID.class);
        ritual = registro(habitoRitual, "Me sentí con energía");
        postDiario = registro(habitoPost, null);
        fotoDelRitual = evidenciaDeHabito(ritual, "FOTO", null, "2026-10-14T14:00:00Z");
        evidenciaDeHabito(ritual, "TEXTO", "hecho", "2026-10-14T14:01:00Z");
        publicacion = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.publicaciones_muro (id, autor_id, texto, creado_en) VALUES (?, ?, 'Mi avance', "
                + "TIMESTAMPTZ '2026-10-14 16:00:00+00')", publicacion, ANA);
        accion = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.rocas_diarias (id, participante_id, fecha, posicion, titulo, color, "
                + "puntaje_impacto, eje, completada, completada_en) VALUES (?, ?, DATE '2026-10-14', 1, "
                + "'Llamar a 3 clientes', CAST('VERDE' AS renaser.color_pareto), 5, CAST('CUERPO' AS renaser.eje_objetivo), "
                + "true, TIMESTAMPTZ '2026-10-14 17:00:00+00')", accion, ANA);
        fotoDeLaAccion = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.evidencias (id, participante_id, roca_diaria_id, tipo, bucket, ruta_storage, "
                + "subida_en, es_principal, estado_validacion) VALUES (?, ?, ?, CAST('FOTO' AS renaser.tipo_evidencia), "
                + "'renaser', ?, TIMESTAMPTZ '2026-10-14 17:00:00+00', true, CAST('VALIDA' AS renaser.estado_validacion))",
                fotoDeLaAccion, ANA, accion, "rocas/" + ANA + "/foto.jpg");
    }

    private void usuario(UUID id, String rol, String nombre) {
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol) "
                + "VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario))", id, id + "@renaser.test", nombre, rol);
    }

    private void programa(UUID id) {
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, "
                + "programa_activado_en, timezone) VALUES (?, 59, DATE '2026-08-17', "
                + "TIMESTAMPTZ '2026-08-16 12:00:00+00', 'America/Lima')", id);
    }

    private UUID nuevoHabito(String titulo) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.habitos (id, ambito, titulo, tipo, categoria_clave, exigencia_evidencia) "
                + "VALUES (?, 'SISTEMA', ?, 'CHECKBOX', 'MENTE', CAST('OBLIGATORIA' AS renaser.exigencia_evidencia))",
                id, titulo);
        return id;
    }

    private UUID registro(UUID habito, String respuesta) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.registros_habito (id, participante_id, habito_id, fecha_ejecucion, dia_programa, "
                + "tipo_dia, estado, respuesta_texto, completado_en) VALUES (?, ?, ?, DATE '2026-10-14', 59, "
                + "CAST('DISCIPLINA' AS renaser.tipo_dia), CAST('COMPLETADO' AS renaser.estado_registro), ?, "
                + "TIMESTAMPTZ '2026-10-14 14:00:00+00')", id, ANA, habito, respuesta);
        return id;
    }

    private UUID evidenciaDeHabito(UUID registro, String tipo, String texto, String subidaEn) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.evidencias (id, participante_id, registro_habito_id, tipo, bucket, ruta_storage, "
                + "contenido_texto, subida_en, estado_validacion) VALUES (?, ?, ?, CAST(? AS renaser.tipo_evidencia), ?, ?, ?, "
                + "CAST(? AS timestamptz), CAST('VALIDA' AS renaser.estado_validacion))",
                id, ANA, registro, tipo, texto == null ? "renaser" : null,
                texto == null ? "evidencia-habitos/" + ANA + "/" + id + ".jpg" : null, texto, subidaEn);
        return id;
    }

    // ── la ficha ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("la semana trae la respuesta escrita, cada evidencia con su tipo, la publicacion del post diario y la foto de la accion")
    void laSemanaTraeTodoLoQueLaRespalda() {
        SemanaDeLaPersona ficha = semana.semanaDe(new ConsultaSemanaDePersona(UserId.of(LIDER), FENIX, ANA, VIERNES));

        assertThat(ficha.desde()).isEqualTo(DESDE);
        assertThat(ficha.hasta()).isEqualTo(VIERNES);
        assertThat(ficha.dias()).hasSize(7);
        DiaDeLaFicha dia = ficha.dias().stream().filter(d -> d.fecha().equals(MIERCOLES)).findFirst().orElseThrow();
        ObligacionDelDia delRitual = dia.obligaciones().stream().filter(o -> o.registroId().equals(ritual))
                .findFirst().orElseThrow();
        assertThat(delRitual.respuestaTexto()).isEqualTo("Me sentí con energía");
        assertThat(delRitual.evidenciaId()).isEqualTo(fotoDelRitual);
        assertThat(delRitual.evidencias()).extracting(e -> e.tipo()).containsExactly("FOTO", "TEXTO");
        ObligacionDelDia delPost = dia.obligaciones().stream().filter(o -> o.registroId().equals(postDiario))
                .findFirst().orElseThrow();
        assertThat(delPost.publicacionId()).isEqualTo(publicacion);
        assertThat(delPost.entrega()).isEqualTo("ENTREGADA");
        assertThat(dia.acciones()).singleElement().satisfies(a -> {
            assertThat(a.id()).isEqualTo(accion);
            assertThat(a.completada()).isTrue();
            assertThat(a.evidenciaId()).isEqualTo(fotoDeLaAccion);
        });
    }

    @Test
    @DisplayName("FR-016: abrir la ficha deja una fila en consultas_de_ficha con quien, a quien, el mentor y la semana")
    void registraLaConsulta() {
        // El registro no se borra (V109): las filas de otras pruebas de esta clase siguen ahi, asi que se cuenta la diferencia.
        long antes = consultasDeLaSemana();

        semana.semanaDe(new ConsultaSemanaDePersona(UserId.of(LIDER), FENIX, ANA, VIERNES));

        assertThat(consultasDeLaSemana()).isEqualTo(antes + 1);
        jdbc.queryForList("SELECT rol_consultor, grupo_id, mentor_id, consultada_en FROM renaser.consultas_de_ficha "
                + "WHERE consultor_id = ? AND persona_id = ? AND semana_desde = DATE '2026-10-10'", LIDER, ANA)
                .forEach(fila -> {
                    assertThat(fila.get("rol_consultor")).isEqualTo("MENTOR_LEAD");
                    assertThat(fila.get("grupo_id")).isEqualTo(FENIX);
                    assertThat(fila.get("mentor_id")).isEqualTo(MENTORA);
                });
    }

    private long consultasDeLaSemana() {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.consultas_de_ficha WHERE consultor_id = ? "
                + "AND persona_id = ? AND semana_desde = DATE '2026-10-10'", Long.class, LIDER, ANA);
    }

    @Test
    @DisplayName("la lista del grupo trae a la aprendiz con su color y a la mentora con su programa propio, sin color")
    void laLista() {
        var lista = grupo.grupoDe(new ConsultaGrupoDelLider(UserId.of(LIDER), FENIX, null)).personas();

        assertThat(lista).extracting(PersonaDeLaLista::nombre).containsExactly("Ana Pérez", "Luisa Rojas");
        assertThat(lista.getFirst().color()).isEqualTo(ColorDeLaFila.AMARILLO);
        assertThat(lista.getLast().color()).isEqualTo(ColorDeLaFila.NO_SE_MIDE);
    }

    // ── el Radar de la persona (spec 012) ───────────────────────────────────

    private CodigoRenaserDePersona radarDeAna(UUID actor, Instant cursor) {
        return radar.radarDe(new ConsultaRadarDePersona(UserId.of(actor), FENIX, ANA, cursor));
    }

    private void registroDelRadar(UUID persona, Instant creadoEn, Integer horaPrevista) {
        jdbc.update("INSERT INTO renaser.registros_radar (id, participante_id, que_hago, que_pienso, que_siento, "
                + "nivel_energia, que_evito, creado_en, hora_prevista) VALUES (?, ?, 'Trabajando', 'En la reunión', "
                + "'Tranquila', 7, 'El correo', CAST(? AS timestamptz), ?)", UUID.randomUUID(), persona,
                creadoEn.toString(), horaPrevista);
    }

    @Test
    @DisplayName("spec 012: el Radar trae Mi Radar (encendido, sus horas) y todos los registros, con la hora prevista, de la base de verdad")
    void elRadarDeLaPersona() {
        jdbc.update("UPDATE renaser.participantes_programa SET horas_mi_radar = ARRAY[20, 9, 14]::smallint[], "
                + "dias_mi_radar = CAST('[{\"dia\":7,\"horas\":[]},{\"dia\":6,\"horas\":[11]}]' AS jsonb) "
                + "WHERE usuario_id = ?", ANA);
        registroDelRadar(ANA, Instant.parse("2026-10-09T15:00:00Z"), 10);
        registroDelRadar(ANA, Instant.parse("2026-10-14T19:06:00Z"), 14);
        registroDelRadar(ANA, Instant.parse("2026-10-15T20:20:00Z"), null);

        CodigoRenaserDePersona resultado = radarDeAna(LIDER, null);

        // Guardadas como 20, 9, 14: Mi Radar las devuelve de menor a mayor.
        assertThat(resultado.miRadar().encendido()).isTrue();
        assertThat(resultado.miRadar().horas()).containsExactly(9, 14, 20);
        // Los dias con horas propias salen tal cual los guardo la persona: el sabado la 11 y el domingo «Sin Radar».
        assertThat(resultado.miRadar().dias()).extracting(CodigoRenaserDePersonaFinder.DiaDeLaPersona::dia)
                .containsExactlyInAnyOrder(6, 7);
        assertThat(resultado.miRadar().dias()).filteredOn(dia -> dia.dia() == 6).singleElement()
                .satisfies(dia -> assertThat(dia.horas()).containsExactly(11));
        assertThat(resultado.miRadar().dias()).filteredOn(dia -> dia.dia() == 7).singleElement()
                .satisfies(dia -> assertThat(dia.horas()).isEmpty());
        // Del mas nuevo al mas viejo: el libre, el de Mi Radar y el de la primera semana.
        assertThat(resultado.registros()).extracting(RegistroDelRadar::horaPrevista).containsExactly(null, 14, 10);
        assertThat(resultado.registros().getFirst().queSiento()).isEqualTo("Tranquila");
        assertThat(resultado.siguienteCursor()).isNull();
    }

    @Test
    @DisplayName("spec 012: sin Mi Radar encendido sale apagado y sin horas, y los registros de la primera semana igual estan")
    void elRadarSinMiRadar() {
        registroDelRadar(ANA, Instant.parse("2026-10-09T15:00:00Z"), 10);

        CodigoRenaserDePersona resultado = radarDeAna(LIDER, null);

        assertThat(resultado.miRadar().encendido()).isFalse();
        assertThat(resultado.miRadar().horas()).isEmpty();
        assertThat(resultado.miRadar().dias()).isEmpty();
        assertThat(resultado.registros()).hasSize(1);
    }

    @Test
    @DisplayName("spec 012: en la semana 1 (dia 4) Mi Radar no aplica: miRadar va en null aunque haya horas guardadas, y los registros igual estan")
    void elRadarEnLaSemana1() {
        // Lunes 19 de octubre en Lima: con el Dia 1 el viernes 16, hoy es el dia 4.
        jdbc.update("UPDATE renaser.participantes_programa SET fecha_inicio = DATE '2026-10-16', "
                + "horas_mi_radar = ARRAY[9]::smallint[] WHERE usuario_id = ?", ANA);
        registroDelRadar(ANA, Instant.parse("2026-10-17T15:00:00Z"), 10);

        CodigoRenaserDePersona resultado = radarDeAna(LIDER, null);

        assertThat(resultado.miRadar()).isNull();
        assertThat(resultado.registros()).extracting(RegistroDelRadar::horaPrevista).containsExactly(10);
    }

    @Test
    @DisplayName("spec 012: pagina de a 84 con el cursor del ultimo registro, y la segunda pagina sigue donde quedo la primera")
    void elRadarPagina() {
        Instant primero = Instant.parse("2026-09-01T00:00:00Z");
        for (int hora = 0; hora < 85; hora++) {
            registroDelRadar(ANA, primero.plusSeconds(3600L * hora), null);
        }

        CodigoRenaserDePersona pagina1 = radarDeAna(LIDER, null);
        CodigoRenaserDePersona pagina2 = radarDeAna(LIDER, pagina1.siguienteCursor());

        assertThat(pagina1.registros()).hasSize(84);
        assertThat(pagina1.siguienteCursor()).isEqualTo(primero.plusSeconds(3600L));
        assertThat(pagina2.registros()).hasSize(1);
        assertThat(pagina2.registros().getFirst().creadoEn()).isEqualTo(primero);
        assertThat(pagina2.siguienteCursor()).isNull();
    }

    @Test
    @DisplayName("spec 012, SC-006: una persona de otro grupo es 404 y el mentor, la aprendiz y una cuenta suspendida reciben 403")
    void elRadarNoSeAbreATodos() {
        UUID ajena = UUID.randomUUID();
        usuario(ajena, "APRENDIZ", "Ana de otro grupo");
        programa(ajena);
        try {
            assertThatThrownBy(() -> radar.radarDe(new ConsultaRadarDePersona(UserId.of(LIDER), FENIX, ajena, null)))
                    .isInstanceOf(NoSuchElementException.class);
            assertThatThrownBy(() -> radarDeAna(MENTORA, null)).isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> radarDeAna(ANA, null)).isInstanceOf(NotAuthorizedException.class);
            jdbc.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", LIDER);
            assertThatThrownBy(() -> radarDeAna(LIDER, null)).isInstanceOf(NotAuthorizedException.class);
        } finally {
            jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", ajena);
        }
    }

    @Test
    @DisplayName("spec 012, FR-015: leer el Radar no deja ninguna fila en consultas_de_ficha")
    void elRadarNoRegistraConsulta() {
        long antes = jdbc.queryForObject("SELECT count(*) FROM renaser.consultas_de_ficha WHERE persona_id = ?",
                Long.class, ANA);

        radarDeAna(LIDER, null);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.consultas_de_ficha WHERE persona_id = ?",
                Long.class, ANA)).isEqualTo(antes);
    }

    // ── el registro es append-only (V109) ───────────────────────────────────

    @Test
    @DisplayName("V109: consultas_de_ficha no se edita, no se borra ni se vacia, y solo el liderazgo consulta")
    void elRegistroEsAppendOnly() {
        semana.semanaDe(new ConsultaSemanaDePersona(UserId.of(LIDER), FENIX, ANA, null));

        assertThatThrownBy(() -> jdbc.update("UPDATE renaser.consultas_de_ficha SET mentor_id = NULL"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM renaser.consultas_de_ficha"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE renaser.consultas_de_ficha"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO renaser.consultas_de_ficha (id, consultor_id, rol_consultor, "
                + "persona_id, grupo_id, semana_desde, consultada_en) VALUES (?, ?, 'TRAINEE', ?, ?, DATE '2026-10-10', now())",
                UUID.randomUUID(), ANA, ANA, FENIX)).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── fronteras que se doblan ─────────────────────────────────────────────

    @TestConfiguration
    static class Dobles {

        @Bean
        @Primary
        Clock lunesALas10EnLima() {
            return FixedClock.at(AHORA);
        }

        @Bean
        @Primary
        AcompanamientoFinder grupoFenixConAna() {
            return new GrupoFenixConAna();
        }

        @Bean
        @Primary
        SemaforoFinder anaEnAmarillo() {
            return new AnaEnAmarillo();
        }
    }

    /** Un grupo de Lima con su mentora y una aprendiz. Lo demás responde vacío. */
    static final class GrupoFenixConAna implements AcompanamientoFinder {

        private GrupoAcompanado fenix() {
            return new GrupoAcompanado(FENIX, "Grupo Fénix", UserId.of(MENTORA), UUID.randomUUID(), "America/Lima", 3);
        }

        @Override
        public List<GrupoAcompanado> gruposConMentorVigente(Instant instante) {
            return List.of(fenix());
        }

        @Override
        public List<UserId> aprendicesVigentes(UUID grupoId, Instant instante) {
            return FENIX.equals(grupoId) ? List.of(UserId.of(ANA)) : List.of();
        }

        @Override
        public Optional<GrupoBasico> grupo(UUID grupoId) {
            return FENIX.equals(grupoId)
                    ? Optional.of(new GrupoBasico(FENIX, "Grupo Fénix", UUID.randomUUID(), "America/Lima"))
                    : Optional.empty();
        }

        @Override
        public boolean grupoOperativo(UUID grupoId, Instant instante) {
            return FENIX.equals(grupoId);
        }

        @Override
        public List<TramoDeAcompanamiento> tramosDeMentor(UserId mentorId, Instant desde, Instant hasta) {
            return List.of();
        }

        @Override
        public List<TramoDeAprendiz> tramosDeAprendices(UUID grupoId, Instant desde, Instant hasta) {
            return List.of();
        }

        @Override
        public List<UserId> acompanantesVigentes(UUID grupoId, Instant instante) {
            return List.of();
        }

        @Override
        public List<UserId> integrantesVigentes(UUID grupoId, Instant instante) {
            return List.of();
        }

        @Override
        public boolean esIntegranteVigente(UUID grupoId, UserId usuarioId, Instant instante) {
            return false;
        }

        @Override
        public boolean acompanaVigente(UserId actorId, UUID grupoId, Instant instante) {
            return false;
        }

        @Override
        public List<GrupoConAprendices> gruposOperativos(Instant instante) {
            return List.of();
        }

        @Override
        public List<GrupoAcompanado> gruposRegularesEnCurso(Instant instante) {
            return gruposConMentorVigente(instante);
        }

        @Override
        public List<GrupoAcompanado> gruposRegularesEnCursoEntre(LocalDate desde, LocalDate hasta) {
            return gruposConMentorVigente(AHORA);
        }
    }

    /** Ana y su mentora tienen 70 % en la ventana que se pida; nadie más se mide. */
    static final class AnaEnAmarillo implements SemaforoFinder {

        private static Map<UserId, VentanaDelSemaforo> ventanas(Collection<UserId> pedidos, LocalDate desde,
                                                                boolean cerrada) {
            Map<UserId, VentanaDelSemaforo> leidas = new java.util.LinkedHashMap<>();
            for (UserId id : pedidos) {
                if (id.value().equals(ANA) || id.value().equals(MENTORA)) {
                    leidas.put(id, new VentanaDelSemaforo(desde, desde.plusDays(6), new BigDecimal("70.0"),
                            ColorSemaforo.AMARILLO, 7, cerrada, List.of()));
                }
            }
            return leidas;
        }

        @Override
        public Map<UserId, VentanaDelSemaforo> vigenteDe(Collection<UserId> participantes) {
            return ventanas(participantes, LocalDate.of(2026, 10, 12), false);
        }

        @Override
        public Map<UserId, VentanaDelSemaforo> semanaDe(Collection<UserId> participantes, LocalDate semanaHasta) {
            return ventanas(participantes, semanaHasta.minusDays(6), true);
        }

        @Override
        public DetalleDelSemaforo detalleDe(UserId participante, int semanas) {
            return DetalleDelSemaforo.noAplica(ZoneId.of("America/Lima"), true);
        }
    }
}
