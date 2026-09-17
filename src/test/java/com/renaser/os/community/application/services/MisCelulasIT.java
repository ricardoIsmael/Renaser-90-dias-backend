package com.renaser.os.community.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.community.application.ports.in.celula.AsignarAprendizCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.AsignarAprendizCelulaUseCase.AsignarAprendizCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.AsignarMentorCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.AsignarMentorCelulaUseCase.AsignarMentorCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.ConsultarMiCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.ConsultarMisCelulasUseCase;
import com.renaser.os.community.application.ports.in.celula.SumarAprendizAGrupoUseCase;
import com.renaser.os.community.application.ports.in.celula.SumarAprendizAGrupoUseCase.SumarAprendizAGrupoCommand;
import com.renaser.os.community.domain.model.celula.CelulaId;
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
 * La info de cada grupo muestra SU gente, no la del principal (D-142).
 *
 * <p><b>El bug que fija.</b> La pantalla "info del grupo" de la app se armaba entera con
 * {@code /me/cell} y {@code /me/cell/members}, que responden por el grupo que nombra
 * {@code participantes_programa.celula_id}. Como ese puntero es de un solo valor y el alta adicional
 * no lo mueve (D-139), abrir la info de CUALQUIER grupo mostraba el nombre, el mentor y los
 * integrantes del principal. Reporte del dueño (2026-09-17): <i>"los grupos nuevos no mantienen en
 * info sus propios integrantes sino los del general"</i>.
 *
 * <p><b>Por qué contra Postgres.</b> Porque lo que se cambia es de qué tabla sale la respuesta —del
 * puntero al historial— y un doble no distingue las dos: ambas devolverían lo que el mock diga. Acá
 * las dos existen, con datos distintos a propósito, y la prueba falla si alguien vuelve a leer el
 * puntero.
 *
 * <p>La última prueba fija que los endpoints VIEJOS no cambiaron: hay un APK repartido que los usa.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MisCelulasIT {

    @Autowired
    private ConsultarMisCelulasUseCase misCelulas;
    @Autowired
    private ConsultarMiCelulaUseCase miCelula;
    @Autowired
    private AsignarAprendizCelulaUseCase trasladar;
    @Autowired
    private SumarAprendizAGrupoUseCase sumar;
    @Autowired
    private AsignarMentorCelulaUseCase asignarMentor;
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
                VALUES (?, 'Cohorte info por grupo', CURRENT_DATE - 10)
                """, cohorteId);
        admin = nuevoUsuario("ADMIN", "Admin de prueba");
    }

    @AfterEach
    void limpiar() {
        celulas.forEach(id -> jdbcTemplate.update(
                "UPDATE renaser.celulas SET mentor_id = NULL WHERE id = ?", id));
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.perfiles_mentor WHERE usuario_id = ?", id));
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        celulas.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.celulas WHERE id = ?", id));
        jdbcTemplate.update("DELETE FROM renaser.politicas_mentoria WHERE cohorte_id = ?", cohorteId);
        jdbcTemplate.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorteId);
    }

    // ── Casos ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cada grupo lista SUS integrantes: el segundo ya no devuelve los del principal")
    void cadaGrupoListaSuPropiaGente() {
        UUID general = nuevoGrupo("General");
        UUID nuevo = nuevoGrupo("Nuevo");
        UserId aprendiz = nuevoAprendizInscrito("Protagonista");
        UserId soloDelGeneral = nuevoAprendizInscrito("Solo del general");
        UserId soloDelNuevo = nuevoAprendizInscrito("Solo del nuevo");

        trasladar.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(general), aprendiz));
        trasladar.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(general), soloDelGeneral));
        trasladar.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(nuevo), soloDelNuevo));
        // El alta adicional deja el puntero en `general`: ahi nacia el bug.
        sumar.sumar(new SumarAprendizAGrupoCommand(admin, CelulaId.of(nuevo), aprendiz));

        assertThat(misCelulas.integrantesDe(aprendiz, CelulaId.of(nuevo)))
                .as("la info del grupo nuevo trae a los del grupo nuevo")
                .extracting(p -> p.id())
                .containsExactlyInAnyOrder(aprendiz, soloDelNuevo);
        assertThat(misCelulas.integrantesDe(aprendiz, CelulaId.of(general)))
                .as("y la del general, a los del general")
                .extracting(p -> p.id())
                .containsExactlyInAnyOrder(aprendiz, soloDelGeneral);
    }

    @Test
    @DisplayName("misCelulas devuelve los DOS grupos, con el principal primero y su conteo propio")
    void devuelveLosDosGruposConElPrincipalPrimero() {
        UUID general = nuevoGrupo("General");
        UUID nuevo = nuevoGrupo("Nuevo");
        UserId aprendiz = nuevoAprendizInscrito("Protagonista");
        UserId soloDelNuevo = nuevoAprendizInscrito("Solo del nuevo");

        trasladar.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(general), aprendiz));
        trasladar.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(nuevo), soloDelNuevo));
        sumar.sumar(new SumarAprendizAGrupoCommand(admin, CelulaId.of(nuevo), aprendiz));

        var suyos = misCelulas.misCelulas(aprendiz);

        assertThat(suyos).hasSize(2);
        assertThat(suyos.get(0).celula().id()).as("el principal —el del puntero— primero").isEqualTo(CelulaId.of(general));
        /* El conteo sale del historial, igual que la lista: hasta ahora `/me/cell` lo sacaba del
           puntero, asi que un grupo podia decir un numero y listar otro. */
        assertThat(suyos.get(1).cantidadMiembros()).as("el nuevo cuenta a sus dos").isEqualTo(2);
    }

    @Test
    @DisplayName("Los integrantes de un grupo ajeno son 403, no una lista vacia")
    void grupoAjenoEsRechazado() {
        UUID propio = nuevoGrupo("Propio");
        UUID ajeno = nuevoGrupo("Ajeno");
        UserId aprendiz = nuevoAprendizInscrito("Protagonista");
        UserId extrano = nuevoAprendizInscrito("Extrano");

        trasladar.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(propio), aprendiz));
        trasladar.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(ajeno), extrano));

        // Una lista vacia seria indistinguible de "ese grupo no tiene a nadie", y con eso se podrian
        // barrer ids para inferir que grupos existen y cuales estan poblados.
        assertThatThrownBy(() -> misCelulas.integrantesDe(aprendiz, CelulaId.of(ajeno)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("Los endpoints viejos no cambiaron: /me/cell sigue respondiendo por el principal")
    void losEndpointsViejosSiguenIgual() {
        UUID general = nuevoGrupo("General");
        UUID nuevo = nuevoGrupo("Nuevo");
        UserId aprendiz = nuevoAprendizInscrito("Protagonista");
        UserId soloDelNuevo = nuevoAprendizInscrito("Solo del nuevo");

        trasladar.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(general), aprendiz));
        trasladar.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(nuevo), soloDelNuevo));
        sumar.sumar(new SumarAprendizAGrupoCommand(admin, CelulaId.of(nuevo), aprendiz));

        /* Hay un APK repartido que usa estos dos. Siguen respondiendo por el grupo principal, que es
           la respuesta correcta a la pregunta que hacen. Si algun dia se los cambia, que sea una
           decision y no un efecto de haber agregado los nuevos. */
        assertThat(miCelula.miCelula(aprendiz)).isPresent()
                .get().extracting(mc -> mc.celula().id()).isEqualTo(CelulaId.of(general));
        assertThat(miCelula.misCompaneros(aprendiz)).extracting(p -> p.id()).containsExactly(aprendiz);
    }

    @Test
    @DisplayName("El MENTOR del grupo tambien lo ve y ve a su gente: no es una lectura solo de alumnos")
    void elMentorTambienVeSuGrupoYSuGente() {
        UUID grupo = nuevoGrupo("Con mentor");
        UserId mentor = nuevoMentorConPerfil();
        UserId aprendiz = nuevoAprendizInscrito("Alumno del grupo");

        asignarMentor.asignar(new AsignarMentorCelulaCommand(admin, CelulaId.of(grupo), mentor));
        trasladar.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(grupo), aprendiz));

        /* El intento anterior de mostrar el numero de integrantes en la cabecera del chat se
           revirtio justamente porque preguntaba con `/me/cell`, que a un mentor le responde
           "no tienes grupo". Esta lectura contesta otra pregunta y a el si le sirve. */
        assertThat(misCelulas.misCelulas(mentor)).extracting(mc -> mc.celula().id())
                .containsExactly(CelulaId.of(grupo));
        assertThat(misCelulas.integrantesDe(mentor, CelulaId.of(grupo)))
                .extracting(p -> p.id()).containsExactly(aprendiz);
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

    private UserId nuevoMentorConPerfil() {
        UserId id = nuevoUsuario("MENTOR", "Mentor de prueba");
        jdbcTemplate.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?)", id.value());
        return id;
    }

    /** Aprendiz CON fila de programa: sin ella el puntero no tendria donde escribirse (E-186). */
    private UserId nuevoAprendizInscrito(String nombre) {
        UserId id = nuevoUsuario("APRENDIZ", nombre);
        jdbcTemplate.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, fecha_inicio, dia_programa)
                VALUES (?, CURRENT_DATE - 5, 5)
                """, id.value());
        return id;
    }
}
