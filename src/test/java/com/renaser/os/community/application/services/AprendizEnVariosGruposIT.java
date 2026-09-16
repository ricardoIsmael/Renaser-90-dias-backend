package com.renaser.os.community.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.application.ports.in.celula.AsignarAprendizCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.AsignarAprendizCelulaUseCase.AsignarAprendizCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.QuitarAprendizCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.QuitarAprendizCelulaUseCase.QuitarAprendizCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.SumarAprendizAGrupoUseCase;
import com.renaser.os.community.application.ports.in.celula.SumarAprendizAGrupoUseCase.SumarAprendizAGrupoCommand;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Un aprendiz en varios grupos a la vez, contra Postgres de verdad (D-139).
 *
 * <p><b>Por qué esto no lo puede probar un doble.</b> Lo que impedía esta funcionalidad no era
 * código: era {@code asignaciones_un_grupo_por_aprendiz}, una restricción de exclusión GiST puesta
 * por {@code V45}. Con mocks, abrir la segunda pertenencia "funcionaba" desde el primer día — el
 * INSERT moría recién contra el motor. La primera prueba de esta clase <b>falla contra el esquema
 * anterior a {@code V56}</b> con
 * {@code conflicting key value violates exclusion constraint "asignaciones_un_grupo_por_aprendiz"},
 * y la segunda demuestra que lo que se levantó fue la exclusividad entre grupos y no el control de
 * membresías duplicadas, que sigue en pie con otro nombre.
 *
 * <p>Sin {@code @Transactional} de clase: los casos de uso abren la suya y hay que ver el estado ya
 * comprometido, igual que en {@code ComposicionDeCelulaIT}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AprendizEnVariosGruposIT {

    @Autowired
    private SumarAprendizAGrupoUseCase sumar;
    @Autowired
    private AsignarAprendizCelulaUseCase trasladar;
    @Autowired
    private QuitarAprendizCelulaUseCase quitar;
    @Autowired
    private AcompanamientoFinder acompanamientoFinder;
    @Autowired
    private Clock clock;
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
                VALUES (?, 'Cohorte multigrupo', CURRENT_DATE - 10)
                """, cohorteId);
        admin = nuevoUsuario("ADMIN", "Admin de prueba");
    }

    @AfterEach
    void limpiar() {
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        celulas.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.celulas WHERE id = ?", id));
        jdbcTemplate.update("DELETE FROM renaser.politicas_mentoria WHERE cohorte_id = ?", cohorteId);
        jdbcTemplate.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorteId);
    }

    // ── Casos ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Sumar lo deja en los DOS grupos: dos intervalos abiertos y dos chats que lo incluyen")
    void sumarDejaAlAprendizEnLosDosGrupos() {
        UUID primero = nuevoGrupo("Primero");
        UUID segundo = nuevoGrupo("Segundo");
        UserId aprendiz = nuevoAprendizInscrito();

        trasladar.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(primero), aprendiz));
        sumar.sumar(new SumarAprendizAGrupoCommand(admin, CelulaId.of(segundo), aprendiz));

        /* Contra el esquema anterior a V56, este INSERT no llegaba a existir:
           asignaciones_un_grupo_por_aprendiz prohibia dos intervalos solapados del mismo usuario. */
        assertThat(intervalosVigentesDe(aprendiz, primero)).as("sigue en el primero").isEqualTo(1);
        assertThat(intervalosVigentesDe(aprendiz, segundo)).as("y ademas en el segundo").isEqualTo(1);
        assertThat(acompanamientoFinder.integrantesVigentes(primero, clock.now())).contains(aprendiz);
        assertThat(acompanamientoFinder.integrantesVigentes(segundo, clock.now())).contains(aprendiz);
        // El puntero no se mueve: nombra al grupo PRINCIPAL, que es el primero.
        assertThat(punteroDe(aprendiz)).as("el puntero sigue en el principal").isEqualTo(primero);
    }

    @Test
    @DisplayName("La base sigue rechazando dos membresias vigentes en el MISMO grupo")
    void laBaseRechazaLaMembresiaDuplicada() {
        UUID grupo = nuevoGrupo("Fenix");
        UserId aprendiz = nuevoAprendizInscrito();
        sumar.sumar(new SumarAprendizAGrupoCommand(admin, CelulaId.of(grupo), aprendiz));

        /* Se inserta a mano y con OTRA clave de operacion, que es justo lo que el indice unico
           `asignaciones_celula_operacion_uk` NO puede frenar. Quien lo frena es la restriccion que
           V56 puso en lugar de la que quito. */
        assertThatThrownBy(() -> insertarPertenencia(grupo, aprendiz))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("asignaciones_una_vez_en_cada_grupo");
    }

    @Test
    @DisplayName("Sumarlo dos veces al mismo grupo no abre una segunda membresia")
    void sumarDosVecesNoDuplica() {
        UUID grupo = nuevoGrupo("Fenix");
        UserId aprendiz = nuevoAprendizInscrito();
        var comando = new SumarAprendizAGrupoCommand(admin, CelulaId.of(grupo), aprendiz);

        sumar.sumar(comando);
        sumar.sumar(comando);

        assertThat(filasDe(aprendiz)).as("una sola fila, no dos").isEqualTo(1);
    }

    @Test
    @DisplayName("Retirarlo del grupo ADICIONAL no le borra el puntero del principal")
    void retirarDelAdicionalNoLeBorraElPuntero() {
        UUID principal = nuevoGrupo("Principal");
        UUID adicional = nuevoGrupo("Adicional");
        UserId aprendiz = nuevoAprendizInscrito();
        trasladar.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(principal), aprendiz));
        sumar.sumar(new SumarAprendizAGrupoCommand(admin, CelulaId.of(adicional), aprendiz));

        quitar.quitar(new QuitarAprendizCelulaCommand(admin, CelulaId.of(adicional), aprendiz));

        assertThat(intervalosVigentesDe(aprendiz, adicional)).as("sale del adicional").isZero();
        assertThat(intervalosVigentesDe(aprendiz, principal)).as("sigue en el principal").isEqualTo(1);
        assertThat(punteroDe(aprendiz)).as("y el puntero no se toco").isEqualTo(principal);
    }

    @Test
    @DisplayName("Sin grupo previo, sumar estrena el puntero: el primero que se suma es el principal")
    void sumarSinGrupoPrevioEstrenaElPuntero() {
        UUID grupo = nuevoGrupo("Fenix");
        UserId aprendiz = nuevoAprendizInscrito();

        sumar.sumar(new SumarAprendizAGrupoCommand(admin, CelulaId.of(grupo), aprendiz));

        assertThat(punteroDe(aprendiz)).isEqualTo(grupo);
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

    /** Aprendiz CON fila de programa: sin ella el puntero no tendria donde escribirse (E-186). */
    private UserId nuevoAprendizInscrito() {
        UserId id = nuevoUsuario("APRENDIZ", "Aprendiz de prueba");
        jdbcTemplate.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, fecha_inicio, dia_programa)
                VALUES (?, CURRENT_DATE - 5, 5)
                """, id.value());
        return id;
    }

    private void insertarPertenencia(UUID grupoId, UserId usuarioId) {
        jdbcTemplate.update("""
                INSERT INTO renaser.asignaciones_celula
                       (id, celula_id, usuario_id, funcion, inicio, fin, motivo, clave_operacion)
                VALUES (?, ?, ?, CAST('APRENDIZ' AS renaser.funcion_acompanamiento),
                        now() - interval '1 day', NULL,
                        CAST('ADMINISTRATIVO' AS renaser.motivo_asignacion), ?)
                """, UUID.randomUUID(), grupoId, usuarioId.value(), "fixture|" + UUID.randomUUID());
    }

    private int intervalosVigentesDe(UserId usuarioId, UUID grupoId) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM renaser.asignaciones_celula
                WHERE usuario_id = ? AND celula_id = ? AND fin IS NULL
                """, Integer.class, usuarioId.value(), grupoId);
        return n == null ? 0 : n;
    }

    private int filasDe(UserId usuarioId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM renaser.asignaciones_celula WHERE usuario_id = ?",
                Integer.class, usuarioId.value());
        return n == null ? 0 : n;
    }

    private UUID punteroDe(UserId usuarioId) {
        return jdbcTemplate.queryForObject(
                "SELECT celula_id FROM renaser.participantes_programa WHERE usuario_id = ?",
                UUID.class, usuarioId.value());
    }
}
