package com.renaser.os.community.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.application.ports.in.celula.AsignarAprendizCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.AsignarAprendizCelulaUseCase.AsignarAprendizCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.QuitarAprendizCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.QuitarAprendizCelulaUseCase.QuitarAprendizCelulaCommand;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionInvalidaException;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La composicion manual de un grupo, contra Postgres de verdad.
 *
 * <p><b>Que demuestra que los dobles no pueden.</b> {@code ComposicionDeCelulaServiceTest} prueba
 * que el servicio LLAMA a lo que tiene que llamar. Lo que no puede probar es que lo escrito quede
 * donde debe: que la fila de {@code asignaciones_celula} exista con su intervalo abierto, que el
 * puntero de {@code participantes_programa} apunte al mismo grupo, y que repetir el comando choque
 * contra {@code asignaciones_celula_operacion_uk} en vez de abrir un segundo intervalo. Eso solo
 * lo responde el motor.
 *
 * <p>Y prueba el efecto que da nombre a este cambio: hasta ahora asignar escribia SOLO el puntero,
 * asi que la consulta de integrantes —la que alimenta el chat y el seguimiento— devolvia una lista
 * vacia para un grupo que el panel mostraba lleno. La ultima prueba de esta clase falla contra el
 * codigo anterior.
 *
 * <p>Sin {@code @Transactional} de clase: los casos de uso abren la suya y hay que ver el estado
 * ya comprometido.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ComposicionDeCelulaIT {

    @Autowired
    private AsignarAprendizCelulaUseCase asignar;
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
                VALUES (?, 'Cohorte composicion', CURRENT_DATE - 10)
                """, cohorteId);
        admin = nuevoUsuario("ADMIN", "Admin de prueba");
    }

    @AfterEach
    void limpiar() {
        // asignaciones_celula y participantes_programa caen por ON DELETE CASCADE.
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        celulas.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.celulas WHERE id = ?", id));
        jdbcTemplate.update("DELETE FROM renaser.politicas_mentoria WHERE cohorte_id = ?", cohorteId);
        jdbcTemplate.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorteId);
    }

    // ── Casos ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Asignar escribe el intervalo Y el puntero, y el grupo pasa a tener integrantes")
    void elAltaDejaHistorialPunteroYPertenencia() {
        UUID grupo = nuevoGrupo("Fenix", true);
        UserId aprendiz = nuevoAprendizInscrito();

        asignar.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(grupo), aprendiz));

        assertThat(intervalosVigentesDe(aprendiz, grupo))
                .as("la fila de historial con su intervalo abierto")
                .isEqualTo(1);
        assertThat(punteroDe(aprendiz)).as("el puntero de proyeccion").isEqualTo(grupo);
        /* La prueba que falla contra el codigo anterior: antes solo se escribia el puntero, asi
           que esta consulta —la del chat y la del seguimiento— devolvia lista vacia. */
        assertThat(acompanamientoFinder.integrantesVigentes(grupo, clock.now())).contains(aprendiz);
    }

    @Test
    @DisplayName("Repetir el mismo alta no abre una segunda membresia")
    void elAltaRepetidaNoDuplica() {
        UUID grupo = nuevoGrupo("Fenix", true);
        UserId aprendiz = nuevoAprendizInscrito();
        var comando = new AsignarAprendizCelulaCommand(admin, CelulaId.of(grupo), aprendiz);

        asignar.asignar(comando);
        asignar.asignar(comando);

        assertThat(filasDe(aprendiz)).as("una sola fila, no dos").isEqualTo(1);
    }

    @Test
    @DisplayName("Mover a otro grupo cierra el intervalo anterior y conserva su historia")
    void moverCierraElAnteriorSinBorrarlo() {
        UUID origen = nuevoGrupo("Origen", true);
        UUID destino = nuevoGrupo("Destino", true);
        UserId aprendiz = nuevoAprendizInscrito();

        asignar.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(origen), aprendiz));
        asignar.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(destino), aprendiz));

        assertThat(filasDe(aprendiz)).as("las dos pertenencias quedan en el historial").isEqualTo(2);
        assertThat(intervalosVigentesDe(aprendiz, origen)).as("la anterior, cerrada").isZero();
        assertThat(intervalosVigentesDe(aprendiz, destino)).as("la nueva, abierta").isEqualTo(1);
        assertThat(punteroDe(aprendiz)).isEqualTo(destino);
    }

    @Test
    @DisplayName("Retirar desde el grupo EQUIVOCADO no le toca su pertenencia real")
    void retirarDesdeOtroGrupoNoLoSacaDelSuyo() {
        UUID suGrupo = nuevoGrupo("El suyo", true);
        UUID otroGrupo = nuevoGrupo("Otro", true);
        UserId aprendiz = nuevoAprendizInscrito();
        asignar.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(suGrupo), aprendiz));

        assertThatThrownBy(() ->
                quitar.quitar(new QuitarAprendizCelulaCommand(admin, CelulaId.of(otroGrupo), aprendiz)))
                .isInstanceOf(AsignacionInvalidaException.class);

        assertThat(intervalosVigentesDe(aprendiz, suGrupo)).as("sigue en el suyo").isEqualTo(1);
        assertThat(punteroDe(aprendiz)).isEqualTo(suGrupo);
    }

    @Test
    @DisplayName("Retirar del grupo correcto cierra el intervalo y limpia el puntero")
    void retirarCierraYLimpia() {
        UUID grupo = nuevoGrupo("Fenix", true);
        UserId aprendiz = nuevoAprendizInscrito();
        asignar.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(grupo), aprendiz));

        quitar.quitar(new QuitarAprendizCelulaCommand(admin, CelulaId.of(grupo), aprendiz));

        assertThat(intervalosVigentesDe(aprendiz, grupo)).isZero();
        assertThat(filasDe(aprendiz)).as("la historia se conserva").isEqualTo(1);
        assertThat(punteroDe(aprendiz)).isNull();
        assertThat(acompanamientoFinder.integrantesVigentes(grupo, clock.now())).doesNotContain(aprendiz);
    }

    @Test
    @DisplayName("El cupo del servidor rechaza al que sobra, aunque el cliente lo pida igual")
    void elCupoSeSostieneEnElServidor() {
        UUID grupo = nuevoGrupoConCupo("Grupo de 10", 10);
        for (int i = 0; i < 10; i++) {
            asignar.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(grupo), nuevoAprendizInscrito()));
        }
        UserId elQueSobra = nuevoAprendizInscrito();

        assertThatThrownBy(() ->
                asignar.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(grupo), elQueSobra)))
                .isInstanceOf(AsignacionInvalidaException.class)
                .hasMessageContaining("cupo");

        assertThat(filasDe(elQueSobra)).isZero();
    }

    @Test
    @DisplayName("Un grupo fuera de su periodo no tiene integrantes vigentes: el acceso se revoca solo")
    void elGrupoCerradoRevocaElAccesoSinJob() {
        UUID vigente = nuevoGrupo("Corriendo", true);
        UUID cerrado = nuevoGrupoCerrado("Termino la semana pasada");
        UserId enVigente = nuevoAprendizInscrito();
        UserId enCerrado = nuevoAprendizInscrito();

        asignar.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(vigente), enVigente));
        // Al cerrado se entra por SQL: el caso de uso lo aceptaria igual —componer un grupo
        // terminado es una decision administrativa— pero lo que se prueba aca es la LECTURA.
        insertarPertenencia(cerrado, enCerrado);

        assertThat(acompanamientoFinder.integrantesVigentes(vigente, clock.now())).contains(enVigente);
        /* La fila de asignacion sigue VIVA: cerrar el periodo del grupo no cierra las asignaciones.
           Que la lista venga vacia demuestra que la revocacion sale del periodo y no de un barrido
           que podria no haber corrido (ARF-18, V17). */
        assertThat(intervalosVigentesDe(enCerrado, cerrado)).as("la fila sigue abierta").isEqualTo(1);
        assertThat(acompanamientoFinder.integrantesVigentes(cerrado, clock.now())).isEmpty();
        assertThat(acompanamientoFinder.esIntegranteVigente(cerrado, enCerrado, clock.now())).isFalse();
    }

    @Test
    @DisplayName("Un grupo PROGRAMADO tampoco da acceso hoy: existir no es estar corriendo")
    void elGrupoFuturoNoDaAccesoTodavia() {
        UUID futuro = nuevoGrupoFuturo("Arranca el mes que viene");
        UserId aprendiz = nuevoAprendizInscrito();
        insertarPertenencia(futuro, aprendiz);

        assertThat(acompanamientoFinder.esIntegranteVigente(futuro, aprendiz, clock.now())).isFalse();
    }

    // ── Semilla ─────────────────────────────────────────────────────────────

    private UUID nuevoGrupo(String nombre, boolean conPeriodoVigente) {
        UUID id = UUID.randomUUID();
        if (conPeriodoVigente) {
            jdbcTemplate.update("""
                    INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin)
                    VALUES (?, ?, ?, CAST('REGULAR' AS renaser.tipo_celula), CURRENT_DATE - 5, CURRENT_DATE + 20)
                    """, id, nombre, cohorteId);
        } else {
            jdbcTemplate.update("""
                    INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo)
                    VALUES (?, ?, ?, CAST('REGULAR' AS renaser.tipo_celula))
                    """, id, nombre, cohorteId);
        }
        celulas.add(id);
        return id;
    }

    private UUID nuevoGrupoConCupo(String nombre, int cupo) {
        UUID id = nuevoGrupo(nombre, true);
        jdbcTemplate.update("UPDATE renaser.celulas SET capacidad_maxima = ? WHERE id = ?", cupo, id);
        return id;
    }

    private UUID nuevoGrupoCerrado(String nombre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin)
                VALUES (?, ?, ?, CAST('REGULAR' AS renaser.tipo_celula), CURRENT_DATE - 40, CURRENT_DATE - 7)
                """, id, nombre, cohorteId);
        celulas.add(id);
        return id;
    }

    private UUID nuevoGrupoFuturo(String nombre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin)
                VALUES (?, ?, ?, CAST('REGULAR' AS renaser.tipo_celula), CURRENT_DATE + 10, CURRENT_DATE + 40)
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

    /** Aprendiz CON fila de programa: sin ella el puntero no tendria donde escribirse. */
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
