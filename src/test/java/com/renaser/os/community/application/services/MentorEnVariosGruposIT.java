package com.renaser.os.community.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.community.application.ports.in.celula.AsignarMentorCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.AsignarMentorCelulaUseCase.AsignarMentorCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.SumarMentorAGrupoUseCase;
import com.renaser.os.community.application.ports.in.celula.SumarMentorAGrupoUseCase.SumarMentorAGrupoCommand;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionInvalidaException;
import com.renaser.os.community.domain.model.celula.CelulaId;
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
 * Un mentor al frente de varios grupos a la vez, contra Postgres de verdad (D-141).
 *
 * <p><b>Por qué esto no lo puede probar un doble.</b> Lo que lo impedía estaba en el esquema, y en
 * DOS lugares a la vez: {@code celulas.mentor_id UNIQUE} (V1) y
 * {@code asignaciones_una_celula_por_mentor} (V45). Con mocks, abrir la segunda jefatura
 * "funcionaba" desde el primer día — el INSERT moría recién contra el motor, y levantar una sola de
 * las dos no habría alcanzado. La primera prueba de esta clase <b>falla contra el esquema anterior
 * a {@code V58}</b>.
 *
 * <p>Las otras dos existen para fijar lo que NO se levantó: un grupo sigue teniendo un solo mentor,
 * y el traslado sigue trasladando. Esa última es la que protege de la regresión más cara: si
 * alguien "arreglara" {@code asignar} para que también sume, los grupos dejarían de poder cambiar
 * de mentor.
 *
 * <p>Sin {@code @Transactional} de clase: los casos de uso abren la suya y hay que ver el estado ya
 * comprometido, igual que en {@code AprendizEnVariosGruposIT}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MentorEnVariosGruposIT {

    @Autowired
    private SumarMentorAGrupoUseCase sumar;
    @Autowired
    private AsignarMentorCelulaUseCase trasladar;
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
                VALUES (?, 'Cohorte multimentor', CURRENT_DATE - 10)
                """, cohorteId);
        admin = nuevoUsuario("ADMIN", "Admin de prueba");
    }

    @AfterEach
    void limpiar() {
        celulas.forEach(id -> jdbcTemplate.update(
                "UPDATE renaser.celulas SET mentor_id = NULL WHERE id = ?", id));
        celulas.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.celulas WHERE id = ?", id));
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.perfiles_mentor WHERE usuario_id = ?", id));
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        jdbcTemplate.update("DELETE FROM renaser.politicas_mentoria WHERE cohorte_id = ?", cohorteId);
        jdbcTemplate.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorteId);
    }

    // ── Casos ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Sumar lo deja al frente de los DOS grupos: dos jefaturas abiertas y dos celulas apuntandolo")
    void sumarDejaAlMentorEnLosDosGrupos() {
        UUID primero = nuevoGrupo("Primero");
        UUID segundo = nuevoGrupo("Segundo");
        UserId mentor = nuevoMentorConPerfil();

        trasladar.asignar(new AsignarMentorCelulaCommand(admin, CelulaId.of(primero), mentor));
        sumar.sumar(new SumarMentorAGrupoCommand(admin, CelulaId.of(segundo), mentor));

        /* Las dos comprobaciones son distintas y las dos hacen falta: la primera mira la tabla con
           historia (asignaciones_celula, la que tenia el EXCLUDE) y la segunda la columna
           denormalizada que lee "mi grupo" en Comunidad (celulas.mentor_id, la que tenia el
           UNIQUE). Antes de V58 cada una moria contra una restriccion distinta. */
        assertThat(jefaturasVigentesDe(mentor)).as("dos intervalos abiertos").isEqualTo(2);
        assertThat(gruposQueLoApuntan(mentor)).as("dos celulas con su mentor_id").isEqualTo(2);
    }

    @Test
    @DisplayName("Sumar NO pone un segundo mentor en un grupo que ya tiene uno")
    void sumarNoPoneDosMentoresEnElMismoGrupo() {
        UUID grupo = nuevoGrupo("Fenix");
        UserId mentor = nuevoMentorConPerfil();
        UserId otroMentor = nuevoMentorConPerfil();

        trasladar.asignar(new AsignarMentorCelulaCommand(admin, CelulaId.of(grupo), mentor));

        // Lo que D-141 levanta es "un grupo por mentor". La reciproca sigue en pie: para cambiarle
        // el mentor a un grupo esta el traslado, que cierra al saliente antes de abrir al entrante.
        assertThatThrownBy(() -> sumar.sumar(new SumarMentorAGrupoCommand(admin, CelulaId.of(grupo), otroMentor)))
                .isInstanceOf(AsignacionInvalidaException.class);
    }

    @Test
    @DisplayName("El traslado sigue trasladando: asignar a un segundo grupo deja el primero sin mentor")
    void elTrasladoSigueVaciandoElGrupoAnterior() {
        UUID primero = nuevoGrupo("Primero");
        UUID segundo = nuevoGrupo("Segundo");
        UserId mentor = nuevoMentorConPerfil();

        trasladar.asignar(new AsignarMentorCelulaCommand(admin, CelulaId.of(primero), mentor));
        trasladar.asignar(new AsignarMentorCelulaCommand(admin, CelulaId.of(segundo), mentor));

        /* Esta es la prueba que impide "arreglar" el traslado convirtiendolo en una suma. Su
           comportamiento no cambia con D-141: sigue siendo la operacion con la que un grupo cambia
           de mentor, y por eso vacia el anterior. Quien quiera las dos jefaturas usa `sumar`. */
        assertThat(jefaturasVigentesDe(mentor)).as("una sola jefatura abierta").isEqualTo(1);
        assertThat(mentorDe(primero)).as("el primero quedo sin mentor").isNull();
        assertThat(mentorDe(segundo)).isEqualTo(mentor.value());
    }

    // ── Semilla ─────────────────────────────────────────────────────────────

    private UUID nuevoGrupo(String nombre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin)
                VALUES (?, ?, ?, CAST('REGULAR' AS renaser.tipo_celula), CURRENT_DATE - 5, CURRENT_DATE + 20)
                """, id, nombre, cohorteId);
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

    /** Mentor CON fila en `perfiles_mentor`: sin ella el caso de uso lo rechaza antes de mirar
     * ninguna restriccion, y la prueba pasaria por el motivo equivocado. */
    private UserId nuevoMentorConPerfil() {
        UserId id = nuevoUsuario("MENTOR", "Mentor de prueba");
        jdbcTemplate.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?)", id.value());
        return id;
    }

    private int jefaturasVigentesDe(UserId mentorId) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM renaser.asignaciones_celula
                WHERE usuario_id = ? AND funcion = 'MENTOR' AND fin IS NULL
                """, Integer.class, mentorId.value());
        return n == null ? 0 : n;
    }

    private int gruposQueLoApuntan(UserId mentorId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM renaser.celulas WHERE mentor_id = ?", Integer.class, mentorId.value());
        return n == null ? 0 : n;
    }

    private UUID mentorDe(UUID celulaId) {
        return jdbcTemplate.queryForObject(
                "SELECT mentor_id FROM renaser.celulas WHERE id = ?", UUID.class, celulaId);
    }
}
