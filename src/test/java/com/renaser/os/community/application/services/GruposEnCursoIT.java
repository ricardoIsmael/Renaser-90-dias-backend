package com.renaser.os.community.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.community.application.ports.in.acompanamiento.ConsultarContextoAcompanamientoUseCase;
import com.renaser.os.community.application.ports.in.acompanamiento.TrasladarAprendicesUseCase;
import com.renaser.os.community.application.ports.in.acompanamiento.TrasladarAprendicesUseCase.ResultadoTraslado;
import com.renaser.os.community.application.ports.in.celula.AsignarAprendizCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.AsignarAprendizCelulaUseCase.AsignarAprendizCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.AsignarMentorCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.AsignarMentorCelulaUseCase.AsignarMentorCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase;
import com.renaser.os.community.application.ports.in.celula.ConsultarMiCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.ConsultarMisCelulasUseCase;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D-240 contra Postgres: lo que se lista, lo que se abre y adonde se traslada salen de UNA regla —el
 * grupo está en curso hoy en el día de su cohorte— y el mentor sale de la asignación vigente.
 *
 * <p><b>Por qué contra la base.</b> Lo que cambia es de qué columna sale cada respuesta: el periodo
 * del grupo frente a «no vencido», y {@code asignaciones_celula} frente a {@code celulas.mentor_id}.
 * Acá las dos fuentes existen con datos distintos a propósito.
 *
 * <p><b>El reloj, en la madrugada UTC (E-541).</b> Todas las fechas de la semilla salen de {@link #HOY}, el
 * día en Lima del reloj de la prueba, y no de {@code CURRENT_DATE}. Antes se sembraba con {@code CURRENT_DATE},
 * que Postgres evalúa en la zona de la SESIÓN —pgjdbc la toma de la zona de la JVM—, mientras el dominio deriva el
 * día de programa con el reloj en la zona del participante. En la laptop (JVM en Lima) coincidían siempre; en
 * GitHub (JVM en UTC), entre las 00:00 y las 05:00 UTC la base va un día adelante: el «día 8» de la semilla era
 * el día 7 para el dominio, el traslado no correspondía y devolvía {@code SIN_CAMBIO}. Los periodos de los grupos
 * tenían cinco días de margen; el día del aprendiz, ninguno. Fijar el reloj a esa hora hace que la prueba cubra
 * siempre el caso que antes dependía de cuándo corriera el CI (regla 03).
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, GruposEnCursoIT.RelojDeLaPrueba.class})
class GruposEnCursoIT {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** La hora del run de GitHub que falló: 01:25 UTC del 6/10, que en Lima son las 20:25 del 5/10. */
    private static final Instant MADRUGADA_UTC = Instant.parse("2026-10-06T01:25:00Z");
    /** Hoy para el participante y para su cohorte (los dos en Lima): el 5/10, no el 6/10 de la base en UTC. */
    private static final LocalDate HOY = MADRUGADA_UTC.atZone(LIMA).toLocalDate();

    /** El mismo reloj para el traslado, la vigencia de los grupos y el día derivado del aprendiz. */
    @TestConfiguration
    static class RelojDeLaPrueba {
        @Bean
        @Primary
        RelojFijable relojFijable() {
            return new RelojFijable();
        }
    }

    /**
     * Fijo pero movible: el traslado cierra la asignación a la bienvenida, y {@code PeriodoAsignacion} no admite
     * una que se abra y se cierre en el mismo instante. Con un reloj congelado eso pasaba; en la vida real no.
     */
    static final class RelojFijable implements Clock {
        private final AtomicReference<Instant> ahora = new AtomicReference<>(MADRUGADA_UTC);

        void fijar(Instant instante) {
            ahora.set(instante);
        }

        @Override
        public Instant now() {
            return ahora.get();
        }

        @Override
        public LocalDate today() {
            return ahora.get().atZone(ZoneOffset.UTC).toLocalDate();
        }
    }

    @Autowired
    private ConsultarMisCelulasUseCase misCelulas;
    @Autowired
    private ConsultarMiCelulaUseCase miCelula;
    @Autowired
    private ConsultarCelulasUseCase celulasAdmin;
    @Autowired
    private ConsultarContextoAcompanamientoUseCase contexto;
    @Autowired
    private AsignarAprendizCelulaUseCase trasladarAMano;
    @Autowired
    private AsignarMentorCelulaUseCase asignarMentor;
    @Autowired
    private TrasladarAprendicesUseCase trasladoAutomatico;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private RelojFijable reloj;

    private UUID cohorteId;
    private UserId admin;
    private final List<UUID> celulas = new ArrayList<>();
    private final List<UUID> usuarios = new ArrayList<>();

    @BeforeEach
    void seed() {
        reloj.fijar(MADRUGADA_UTC);
        celulas.clear();
        usuarios.clear();
        cohorteId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.cohortes (id, nombre, fecha_inicio)
                VALUES (?, 'Cohorte grupos en curso', ?)
                """, cohorteId, HOY.minusDays(40));
        admin = nuevoUsuario("ADMIN", "Admin de prueba");
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM renaser.politicas_mentoria WHERE cohorte_id = ?", cohorteId);
        celulas.forEach(id -> jdbcTemplate.update("UPDATE renaser.celulas SET mentor_id = NULL WHERE id = ?", id));
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.perfiles_mentor WHERE usuario_id = ?", id));
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        celulas.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.celulas WHERE id = ?", id));
        jdbcTemplate.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorteId);
    }

    // ── H4: listas = acceso = en curso ──────────────────────────────────────

    @Test
    @DisplayName("E-477: un grupo PROGRAMADO no se lista ni se abre, para el aprendiz ni para su mentor")
    void grupoProgramadoNoSeListaNiSeAbre() {
        UUID programado = nuevoGrupo("Programado", HOY.plusDays(5), HOY.plusDays(35));
        UserId mentor = nuevoMentorConPerfil();
        UserId aprendiz = nuevoAprendizInscrito("Aprendiz", 20);
        asignarMentor.asignar(new AsignarMentorCelulaCommand(admin, CelulaId.of(programado), mentor));
        trasladarAMano.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(programado), aprendiz));

        assertThat(misCelulas.misCelulas(aprendiz)).isEmpty();
        assertThat(miCelula.miCelula(aprendiz)).isEmpty();
        assertThat(misCelulas.misCelulas(mentor)).isEmpty();
        assertThat(contexto.contexto(mentor).asignaciones()).isEmpty();
        assertThat(celulasAdmin.listarPorCohorte(mentor, CohorteId.of(cohorteId))).isEmpty();
        assertThatThrownBy(() -> misCelulas.integrantesDe(aprendiz, CelulaId.of(programado)))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> celulasAdmin.obtener(mentor, CelulaId.of(programado)))
                .isInstanceOf(NotAuthorizedException.class);
        // El admin sigue viendo todo por /admin/cells: es su gestion.
        assertThat(celulasAdmin.obtener(admin, CelulaId.of(programado)).celula().id())
                .isEqualTo(CelulaId.of(programado));
    }

    @Test
    @DisplayName("E-477: un grupo CERRADO tampoco: ni en la lista ni por id, aunque las asignaciones sigan abiertas")
    void grupoCerradoNoSeListaNiSeAbre() {
        UUID cerrado = nuevoGrupo("Cerrado", HOY.minusDays(40), HOY.minusDays(5));
        UserId mentor = nuevoMentorConPerfil();
        UserId aprendiz = nuevoAprendizInscrito("Aprendiz", 20);
        asignarMentor.asignar(new AsignarMentorCelulaCommand(admin, CelulaId.of(cerrado), mentor));
        trasladarAMano.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(cerrado), aprendiz));

        assertThat(misCelulas.misCelulas(aprendiz)).isEmpty();
        assertThat(misCelulas.misCelulas(mentor)).isEmpty();
        assertThatThrownBy(() -> misCelulas.integrantesDe(mentor, CelulaId.of(cerrado)))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> celulasAdmin.obtener(mentor, CelulaId.of(cerrado)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    // ── H6: el mentor sale de la asignacion vigente ─────────────────────────

    @Test
    @DisplayName("E-479: con celulas.mentor_id vaciado a mano, la Tribu y /me/cell siguen nombrando al mentor asignado")
    void elMentorSaleDeLaAsignacion() {
        UUID grupo = nuevoGrupo("En curso", HOY.minusDays(5), HOY.plusDays(20));
        UserId mentor = nuevoMentorConPerfil();
        UserId aprendiz = nuevoAprendizInscrito("Aprendiz", 20);
        asignarMentor.asignar(new AsignarMentorCelulaCommand(admin, CelulaId.of(grupo), mentor));
        trasladarAMano.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(grupo), aprendiz));
        // Lo que hace docs/spec/LIMPIAR_DATOS_PRUEBA_MENTORIA.sql: toca la columna y no la asignacion.
        jdbcTemplate.update("UPDATE renaser.celulas SET mentor_id = NULL WHERE id = ?", grupo);

        assertThat(misCelulas.misCelulas(aprendiz)).singleElement()
                .satisfies(mc -> assertThat(mc.mentor().id()).isEqualTo(mentor));
        assertThat(miCelula.miCelula(aprendiz)).get()
                .satisfies(mc -> assertThat(mc.mentor().id()).isEqualTo(mentor));
        assertThat(celulasAdmin.obtener(mentor, CelulaId.of(grupo)).mentor().id()).isEqualTo(mentor);
    }

    // ── H3: el traslado del dia 8 solo va a grupos en curso ─────────────────

    @Test
    @DisplayName("E-476: sin grupo en curso el aprendiz sigue en la bienvenida y el lider recibe el aviso; con uno, va ahi")
    void trasladoSoloAGruposEnCurso() throws InterruptedException {
        UUID recepcion = nuevaRecepcion();
        nuevoGrupo("Cerrado", HOY.minusDays(40), HOY.minusDays(5));
        nuevoGrupo("Programado", HOY.plusDays(5), HOY.plusDays(35));
        jdbcTemplate.update("""
                INSERT INTO renaser.politicas_mentoria (cohorte_id, celula_recepcion_id, dia_traslado)
                VALUES (?, ?, 8)
                """, cohorteId, recepcion);
        UserId lider = nuevoUsuario("LIDER_MENTORES", "Lider de prueba");
        UserId aprendiz = nuevoAprendizInscrito("Recien llegado", 8);
        // Entro a la bienvenida su Dia 1, a esta misma hora; el traslado del Dia 8 es en la madrugada UTC de hoy.
        reloj.fijar(MADRUGADA_UTC.minus(Duration.ofDays(7)));
        trasladarAMano.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(recepcion), aprendiz));
        reloj.fijar(MADRUGADA_UTC);

        ResultadoTraslado sinGrupo = trasladoAutomatico.ubicar(aprendiz);

        assertThat(sinGrupo.destino()).isEqualTo("SIN_GRUPO_EN_CURSO");
        assertThat(grupoVigenteDe(aprendiz)).isEqualTo(recepcion);
        assertThat(esperarAvisoDeArmado(lider)).as("el aviso al lider, entregado por el outbox").isEqualTo(1);

        UUID enCurso = nuevoGrupo("En curso", HOY.minusDays(5), HOY.plusDays(20));
        ResultadoTraslado conGrupo = trasladoAutomatico.ubicar(aprendiz);

        assertThat(conGrupo.destino()).isEqualTo("GRUPO_ESTABLE");
        assertThat(conGrupo.grupoId()).isEqualTo(enCurso);
        assertThat(grupoVigenteDe(aprendiz)).isEqualTo(enCurso);
    }

    // ── Semilla ─────────────────────────────────────────────────────────────

    private UUID nuevoGrupo(String nombre, LocalDate inicio, LocalDate fin) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin)
                VALUES (?, ?, ?, CAST('REGULAR' AS renaser.tipo_celula), ?, ?)
                """, id, nombre, cohorteId, inicio, fin);
        celulas.add(id);
        return id;
    }

    private UUID nuevaRecepcion() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo)
                VALUES (?, 'Bienvenida', ?, CAST('RECEPCION' AS renaser.tipo_celula))
                """, id, cohorteId);
        celulas.add(id);
        return id;
    }

    private UserId nuevoUsuario(String rol, String nombre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test", nombre, rol);
        usuarios.add(id);
        return UserId.of(id);
    }

    private UserId nuevoMentorConPerfil() {
        UserId id = nuevoUsuario("MENTOR", "Mentor de prueba");
        jdbcTemplate.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?)", id.value());
        return id;
    }

    /**
     * Fixture coherente (regla 03): el dia N arranco hace N-1 dias EN SU ZONA. La zona se escribe explicita porque
     * {@link #HOY} se calculo en ella; el dominio deriva el dia con el reloj en esa misma zona (E-541).
     */
    private UserId nuevoAprendizInscrito(String nombre, int diaPrograma) {
        UserId id = nuevoUsuario("APRENDIZ", nombre);
        jdbcTemplate.update("""
                INSERT INTO renaser.participantes_programa
                    (usuario_id, fecha_inicio, dia_programa, timezone, programa_activado_en)
                VALUES (?, ?, ?, ?, ?)
                """, id.value(), HOY.minusDays(diaPrograma - 1L), diaPrograma, LIMA.getId(),
                Timestamp.from(MADRUGADA_UTC.minus(Duration.ofDays(30))));
        return id;
    }

    private UUID grupoVigenteDe(UserId aprendiz) {
        return jdbcTemplate.queryForObject("""
                SELECT celula_id FROM renaser.asignaciones_celula
                WHERE usuario_id = ? AND funcion = 'APRENDIZ' AND fin IS NULL
                """, UUID.class, aprendiz.value());
    }

    /** El listener corre despues del commit, fuera de este hilo: se espera hasta 10 s. */
    private int esperarAvisoDeArmado(UserId destinatario) throws InterruptedException {
        for (int intento = 0; intento < 50; intento++) {
            Integer avisos = jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM renaser.notificaciones
                    WHERE usuario_id = ? AND tipo = 'ARMADO_DE_GRUPOS'
                    """, Integer.class, destinatario.value());
            if (avisos != null && avisos > 0) {
                return avisos;
            }
            Thread.sleep(200);
        }
        return 0;
    }
}
