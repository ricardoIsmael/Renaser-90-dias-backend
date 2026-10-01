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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
 * <p>Los periodos se escriben relativos a {@code CURRENT_DATE} con varios días de margen, para que la
 * diferencia entre el día de la base (UTC) y el de Lima no cambie el resultado.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GruposEnCursoIT {

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

    private UUID cohorteId;
    private UserId admin;
    private final List<UUID> celulas = new ArrayList<>();
    private final List<UUID> usuarios = new ArrayList<>();

    @BeforeEach
    void seed() {
        celulas.clear();
        usuarios.clear();
        cohorteId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.cohortes (id, nombre, fecha_inicio)
                VALUES (?, 'Cohorte grupos en curso', CURRENT_DATE - 40)
                """, cohorteId);
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
        UUID programado = nuevoGrupo("Programado", "CURRENT_DATE + 5", "CURRENT_DATE + 35");
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
        UUID cerrado = nuevoGrupo("Cerrado", "CURRENT_DATE - 40", "CURRENT_DATE - 5");
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
        UUID grupo = nuevoGrupo("En curso", "CURRENT_DATE - 5", "CURRENT_DATE + 20");
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
        nuevoGrupo("Cerrado", "CURRENT_DATE - 40", "CURRENT_DATE - 5");
        nuevoGrupo("Programado", "CURRENT_DATE + 5", "CURRENT_DATE + 35");
        jdbcTemplate.update("""
                INSERT INTO renaser.politicas_mentoria (cohorte_id, celula_recepcion_id, dia_traslado)
                VALUES (?, ?, 8)
                """, cohorteId, recepcion);
        UserId lider = nuevoUsuario("LIDER_MENTORES", "Lider de prueba");
        UserId aprendiz = nuevoAprendizInscrito("Recien llegado", 8);
        trasladarAMano.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(recepcion), aprendiz));

        ResultadoTraslado sinGrupo = trasladoAutomatico.ubicar(aprendiz);

        assertThat(sinGrupo.destino()).isEqualTo("SIN_GRUPO_EN_CURSO");
        assertThat(grupoVigenteDe(aprendiz)).isEqualTo(recepcion);
        assertThat(esperarAvisoDeArmado(lider)).as("el aviso al lider, entregado por el outbox").isEqualTo(1);

        UUID enCurso = nuevoGrupo("En curso", "CURRENT_DATE - 5", "CURRENT_DATE + 20");
        ResultadoTraslado conGrupo = trasladoAutomatico.ubicar(aprendiz);

        assertThat(conGrupo.destino()).isEqualTo("GRUPO_ESTABLE");
        assertThat(conGrupo.grupoId()).isEqualTo(enCurso);
        assertThat(grupoVigenteDe(aprendiz)).isEqualTo(enCurso);
    }

    // ── Semilla ─────────────────────────────────────────────────────────────

    private UUID nuevoGrupo(String nombre, String inicio, String fin) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin)
                VALUES (?, ?, ?, CAST('REGULAR' AS renaser.tipo_celula), %s, %s)
                """.formatted(inicio, fin), id, nombre, cohorteId);
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

    /** Fixture coherente (regla 03): el dia N arranco hace N-1 dias. */
    private UserId nuevoAprendizInscrito(String nombre, int diaPrograma) {
        UserId id = nuevoUsuario("APRENDIZ", nombre);
        jdbcTemplate.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, fecha_inicio, dia_programa, programa_activado_en)
                VALUES (?, CURRENT_DATE - ?, ?, now() - interval '30 days')
                """, id.value(), diaPrograma - 1, diaPrograma);
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
