package com.renaser.os.leadership;

import com.renaser.os.FormacionesDePrueba;
import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.leadership.application.ports.in.ConsultarAnalisisDePersonaUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarAnalisisDePersonaUseCase.ConsultaAnalisisDePersona;
import com.renaser.os.leadership.application.ports.in.ConsultarAnalisisDePersonaUseCase.ConsultaAnalisisDeUnaPersona;
import com.renaser.os.plataforma.application.services.CopiaDeEstados;
import com.renaser.os.rag.api.Analisis8020Finder.Tipo;
import com.renaser.os.rag.api.Analisis8020Finder.VistaDelAnalisis;
import com.renaser.os.rag.application.services.analisis.JsonDelAnalisis;
import com.renaser.os.rag.application.services.analisis.SnapshotDelAnalisis;
import com.renaser.os.rocks.api.Pareto8020Finder.CifrasDel8020;
import com.renaser.os.rocks.api.Pareto8020Finder.ReglaDeLasVerdes;
import com.renaser.os.rocks.api.Pareto8020Finder.ResumenDelCierre;
import com.renaser.os.shared.domain.FuncionalidadApagadaException;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Quién lee el análisis 80/20 de quién, contra Postgres real y con las reglas de verdad (spec 031, SC-005): el líder, Administración y
 * Alquimista de una persona de un grupo con mentor; el interruptor «Código Renaser en las fichas» (apagado de fábrica para el líder, spec
 * 034); la formación (spec 022); la constancia de cada consulta (FR-021); y un 404 que no revela si existe un análisis. El grupo es el doble
 * de {@link CumplimientoDelLiderIT}, que comparte este contexto de Spring.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, CumplimientoDelLiderIT.Dobles.class})
class AnalisisDelLiderIT {

    private static final Instant AHORA = Instant.parse("2026-10-19T15:00:00Z");
    private static final LocalDate SEMANA_9 = LocalDate.of(2026, 10, 12);
    private static final UUID FENIX = UUID.randomUUID();
    private static final UUID LIDER = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID MENTORA = UUID.randomUUID();
    private static final UUID ANA = UUID.randomUUID();
    private static final UUID AJENA = UUID.randomUUID();

    @Autowired
    private ConsultarAnalisisDePersonaUseCase analisis;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private CopiaDeEstados copiaDeEstados;

    @BeforeEach
    void sembrar() {
        reloj.fijar(AHORA);
        jdbc.update("DELETE FROM renaser.funcionalidades_estado WHERE clave = 'radar.fichas'");
        copiaDeEstados.recargar();
        jdbc.update("DELETE FROM renaser.usuarios WHERE id IN (?, ?, ?, ?, ?)", LIDER, ADMIN, MENTORA, ANA, AJENA);
        usuario(LIDER, "LIDER_MENTORES", "Líder Uno");
        usuario(ADMIN, "ADMIN", "Admin");
        usuario(MENTORA, "MENTOR", "Luisa Rojas");
        usuario(ANA, "APRENDIZ", "Ana Pérez");
        usuario(AJENA, "APRENDIZ", "Ana de otro grupo");
        for (UUID id : List.of(MENTORA, ANA, AJENA)) {
            jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, programa_activado_en, timezone) "
                    + "VALUES (?, 56, DATE '2026-08-17', TIMESTAMPTZ '2026-08-16 12:00:00+00', 'America/Lima')", id);
        }
        analisisListo(ANA, "ESCUELA");
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.funcionalidades_estado WHERE clave = 'radar.fichas'");
        copiaDeEstados.recargar();
        jdbc.update("DELETE FROM renaser.usuarios WHERE id IN (?, ?, ?, ?, ?)", LIDER, ADMIN, MENTORA, ANA, AJENA);
    }

    private void usuario(UUID id, String rol, String nombre) {
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol) VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario))",
                id, id + "@renaser.test", nombre, rol);
        FormacionesDePrueba.alPersonalLeAsignaLaEscuela(jdbc, id, rol);
    }

    private void analisisListo(UUID persona, String formacion) {
        CifrasDel8020 cifras = new CifrasDel8020(SEMANA_9, SEMANA_9.plusDays(6), 9, 7, 4, 0, 50, 38, 78, new ReglaDeLasVerdes(4, 2, 17, 45),
                null, List.of(), List.of(), List.of(), false, new ResumenDelCierre(0, 0, List.of(), List.of()));
        jdbc.update("INSERT INTO renaser.analisis_80_20 (id, participante_id, formacion, periodo, numero, inicio, fin, estado, intentos, "
                        + "ultimo_intento_en, generado_en, creado_en, lo_que_mas_movio, lo_que_se_te_fue, como_estuviste, proximos_pasos, cifras) "
                        + "VALUES (?, ?, ?, 'SEMANA', 9, DATE '2026-10-12', DATE '2026-10-18', 'LISTO', 1, now(), now(), now(), "
                        + "'Entrenar movió más.', 'La llamada quedó pendiente.', 'Tu energía bajó por las tardes.', ARRAY['Haz la llamada primero.'], "
                        + "CAST(? AS jsonb))",
                UUID.randomUUID(), persona, formacion, JsonDelAnalisis.escribir(new SnapshotDelAnalisis(cifras, List.of(), null)));
    }

    private void radarEnFichasPrendido() {
        jdbc.update("INSERT INTO renaser.funcionalidades_estado (clave, prendida_global, version, actualizado_por, actualizado_en) "
                + "VALUES ('radar.fichas', true, nextval('renaser.funcionalidades_version_seq'), ?, now())", ADMIN);
        copiaDeEstados.recargar();
    }

    private VistaDelAnalisis leer(UUID actor, UUID persona) {
        return analisis.analisisDe(new ConsultaAnalisisDePersona(UserId.of(actor), FENIX, persona, Tipo.SEMANA, SEMANA_9));
    }

    private int constancias() {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.consultas_de_ficha WHERE persona_id IN (?, ?)", Integer.class, ANA, AJENA);
    }

    @Test
    @DisplayName("con «Código Renaser en las fichas» prendido el líder lee el análisis listo de una persona de su grupo, con sus cifras, y queda constancia")
    void elLiderLoLee() {
        radarEnFichasPrendido();

        VistaDelAnalisis vista = leer(LIDER, ANA);

        assertThat(vista.estado()).isEqualTo("LISTO");
        assertThat(vista.analisis().loQueMasMovio()).isEqualTo("Entrenar movió más.");
        assertThat(vista.analisis().proximosPasos()).containsExactly("Haz la llamada primero.");
        assertThat(vista.cifras().cumplidas()).isEqualTo(7);
        assertThat(jdbc.queryForList("SELECT consultor_id, rol_consultor, persona_id, grupo_id, semana_desde FROM renaser.consultas_de_ficha "
                + "WHERE persona_id = ?", ANA)).singleElement().satisfies(fila -> {
            assertThat(fila.get("consultor_id")).isEqualTo(LIDER);
            assertThat(fila.get("rol_consultor")).isEqualTo("MENTOR_LEAD");
            assertThat(fila.get("grupo_id")).isEqualTo(FENIX);
            assertThat(fila.get("semana_desde").toString()).isEqualTo("2026-10-12");
        });
    }

    @Test
    @DisplayName("034 / 031-2 A: de fábrica «Código Renaser en las fichas» está apagado para el líder: 404, sin constancia; Administración lo sigue leyendo")
    void deFabricaElLiderNoLoVe() {
        assertThatThrownBy(() -> leer(LIDER, ANA)).isInstanceOf(FuncionalidadApagadaException.class);
        assertThat(constancias()).isZero();

        assertThat(leer(ADMIN, ANA).estado()).isEqualTo("LISTO");
        assertThat(jdbc.queryForObject("SELECT rol_consultor FROM renaser.consultas_de_ficha WHERE persona_id = ?", String.class, ANA))
                .isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("sin pasar por un grupo (spec 021) vale lo mismo: la persona sin grupo se lee y deja constancia")
    void sinGrupo() {
        radarEnFichasPrendido();
        jdbc.update("DELETE FROM renaser.analisis_80_20 WHERE participante_id = ?", ANA);
        analisisListo(AJENA, "ESCUELA");

        VistaDelAnalisis vista = analisis.analisisDeUnaPersona(new ConsultaAnalisisDeUnaPersona(UserId.of(LIDER), AJENA, Tipo.SEMANA, SEMANA_9));

        assertThat(vista.estado()).isEqualTo("LISTO");
        assertThat(constancias()).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-021: una persona que no es del grupo es un 404 sin pista de que exista su análisis; sin permiso, 403; nada deja constancia")
    void quienNoLeeNoAveriguaNada() {
        radarEnFichasPrendido();
        analisisListo(AJENA, "ESCUELA");

        assertThatThrownBy(() -> leer(LIDER, AJENA)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> leer(MENTORA, ANA)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> leer(ANA, ANA)).isInstanceOf(NotAuthorizedException.class);

        assertThat(constancias()).isZero();
    }

    @Test
    @DisplayName("FR-022: lo que quedó de otra formación no se muestra: si Ana pasó al Diplomado su análisis de la Escuela no aparece como listo")
    void otraFormacion() {
        radarEnFichasPrendido();
        jdbc.update("UPDATE renaser.participantes_programa SET formacion = 'DIPLOMADO' WHERE usuario_id = ?", ANA);

        assertThatThrownBy(() -> leer(LIDER, ANA)).isInstanceOf(NoSuchElementException.class);
        VistaDelAnalisis paraAdministracion = leer(ADMIN, ANA);
        assertThat(paraAdministracion.estado()).isNotEqualTo("LISTO");
        assertThat(paraAdministracion.analisis()).isNull();
    }
}
