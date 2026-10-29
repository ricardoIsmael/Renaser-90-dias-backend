package com.renaser.os.habits.application.services;

import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.api.CompletarClaseDiariaHabitoUseCase;
import com.renaser.os.habits.api.CompletarClaseDiariaHabitoUseCase.CompletarClaseDiariaHabitoCommand;
import com.renaser.os.habits.application.ports.in.audiencia.AnotarCambioDeRolUseCase;
import com.renaser.os.habits.application.ports.in.habito.ConsultarMisHabitosUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.ActualizarHabitoUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.ActualizarHabitoUseCase.ActualizarHabitoCommand;
import com.renaser.os.habits.application.ports.in.habitoadmin.EliminarHabitoUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.EliminarHabitoUseCase.EliminarHabitoCommand;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.habits.domain.model.audiencia.Audiencia;
import com.renaser.os.habits.domain.model.audiencia.CodigoDeRol;
import com.renaser.os.habits.domain.model.habito.DetallesHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 001 (D-264) de punta a punta contra Postgres real: la audiencia por rol decide la generación, la vigencia es
 * estricta ("desde mañana", en la zona de la persona) y nada de lo pasado se toca.
 *
 * <p>Fixture coherente (regla 03): el programa arrancó el 28/9, así que el 12/10 es el Día 15. El reloj está a las
 * 15:00 UTC del 12/10 (10:00 en Lima); el "cambio de noche" a las 04:30 UTC del 13/10 es todavía el 12 en Lima.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class HabitosPorRolIT {

    private static final Instant AHORA = Instant.parse("2026-10-12T15:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 12);
    private static final LocalDate MANANA = LocalDate.of(2026, 10, 13);
    private static final LocalDate INICIO = LocalDate.of(2026, 9, 28);
    private static final Audiencia SOLO_MENTORES = Audiencia.de(Set.of(CodigoDeRol.of("MENTOR")));
    private static final DetallesHabito DETALLES = new DetallesHabito("desc", "CUERPO", ExigenciaEvidencia.OPCIONAL,
            false, false);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private GenerarTracksDelDiaUseCase generar;
    @Autowired
    private ActualizarHabitoUseCase actualizar;
    @Autowired
    private EliminarHabitoUseCase eliminar;
    @Autowired
    private AnotarCambioDeRolUseCase anotarCambioDeRol;
    @Autowired
    private ConsultarMisHabitosUseCase misHabitos;
    @Autowired
    private CompletarClaseDiariaHabitoUseCase claseDiaria;
    @Autowired
    private TransactionTemplate transacciones;

    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> habitosTocados = new ArrayList<>();
    private final List<UUID> habitosCreados = new ArrayList<>();
    private UserId admin;
    private UserId aprendiz;
    private UserId mentor;
    private HabitoId habito;

    @BeforeEach
    void preparar() {
        reloj.fijar(AHORA);
        admin = usuario("ADMIN", false);
        aprendiz = usuario("APRENDIZ", true);
        mentor = usuario("MENTOR", true);
        habito = habitoConHorario("Revisión del grupo");
    }

    @AfterEach
    void limpiar() {
        usuarios.forEach(id -> {
            jdbc.update("DELETE FROM renaser.historial_rol_participante WHERE usuario_id = ?", id);
            jdbc.update("DELETE FROM renaser.registros_habito WHERE participante_id = ?", id);
            try {
                jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id);
            } catch (DataAccessException quedaReferenciado) {
                // La base de la suite es descartable.
            }
        });
        // Las versiones de audiencia de un hábito sembrado (Clase diaria) se borran: la base la comparten otras pruebas.
        habitosTocados.forEach(id -> jdbc.update("DELETE FROM renaser.audiencias_habito WHERE habito_id = ?", id));
        habitosCreados.forEach(id -> jdbc.update("DELETE FROM renaser.habitos WHERE id = ?", id));
    }

    @Test
    @DisplayName("restringir hoy: quien todavía no generó su día lo recibe hoy; desde mañana solo los mentores")
    void restringirRigeDesdeManana() {
        restringir(habito, SOLO_MENTORES);

        assertThat(habitosGenerados(aprendiz, HOY)).as("hoy sigue la audiencia de antes").contains(habito);
        assertThat(habitosGenerados(aprendiz, MANANA)).doesNotContain(habito);
        assertThat(habitosGenerados(mentor, MANANA)).contains(habito);
    }

    @Test
    @DisplayName("un cambio a las 23:30 de Lima (04:30 UTC del día siguiente) también espera al día local siguiente")
    void cambioDeNoche() {
        reloj.fijar(Instant.parse("2026-10-13T04:30:00Z"));
        restringir(habito, SOLO_MENTORES);

        assertThat(habitosGenerados(aprendiz, HOY)).contains(habito);
        assertThat(habitosGenerados(aprendiz, MANANA)).doesNotContain(habito);
    }

    @Test
    @DisplayName("el registro de hoy ya generado se conserva y la historia pasada no cambia")
    void registroDeHoySeConserva() {
        habitosGenerados(aprendiz, HOY);
        restringir(habito, SOLO_MENTORES);

        assertThat(registrosDe(aprendiz, habito)).isEqualTo(1);
        habitosGenerados(aprendiz, MANANA);
        assertThat(registrosDe(aprendiz, habito)).as("mañana no suma ni borra").isEqualTo(1);
    }

    @Test
    @DisplayName("quitar los roles el mismo día: mañana vuelve a ser para todos")
    void volverAParaTodos() {
        restringir(habito, SOLO_MENTORES);
        reloj.fijar(AHORA.plusSeconds(3600));
        restringir(habito, Audiencia.PARA_TODOS);

        assertThat(habitosGenerados(aprendiz, MANANA)).contains(habito);
    }

    @Test
    @DisplayName("cambio de rol hoy: hoy recibe los de aprendiz, mañana los de mentor (US4)")
    void cambioDeRol() {
        // La restricción es de hace dos días: hoy ya rige "solo mentores", y lo que cambia es el rol.
        reloj.fijar(AHORA.minusSeconds(2 * 86_400));
        restringir(habito, SOLO_MENTORES);
        reloj.fijar(AHORA);
        jdbc.update("UPDATE renaser.usuarios SET rol = 'MENTOR' WHERE id = ?", aprendiz.value());
        anotarCambioDeRol.anotar(aprendiz, "TRAINEE", "MENTOR", AHORA);
        anotarCambioDeRol.anotar(aprendiz, "TRAINEE", "MENTOR", AHORA); // el outbox puede reentregar

        Integer filas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM renaser.historial_rol_participante WHERE usuario_id = ?", Integer.class,
                aprendiz.value());
        assertThat(filas).as("rol anterior desde siempre + el nuevo, sin duplicar").isEqualTo(2);
        assertThat(habitosGenerados(aprendiz, HOY)).doesNotContain(habito);
        assertThat(habitosGenerados(aprendiz, MANANA)).contains(habito);
    }

    @Test
    @DisplayName("D-265: el guía de aprendiz es un rol aparte para los hábitos: lo de guías le llega a él y no al "
            + "mentor, y lo de mentores no le llega a él")
    void guiaDeAprendizSeFiltraAparte() {
        UserId guia = usuario("GUIA_APRENDIZ", true);
        HabitoId deMentores = habitoConHorario("Solo mentores");
        reloj.fijar(AHORA.minusSeconds(2 * 86_400));
        restringir(habito, Audiencia.de(Set.of(CodigoDeRol.of("TRAINEE_GUIDE"))));
        restringir(deMentores, SOLO_MENTORES);
        reloj.fijar(AHORA);

        assertThat(habitosGenerados(guia, HOY)).contains(habito).doesNotContain(deMentores);
        assertThat(habitosGenerados(mentor, HOY)).contains(deMentores).doesNotContain(habito);
        assertThat(habitosGenerados(aprendiz, HOY)).doesNotContain(habito, deMentores);
    }

    @Test
    @DisplayName("la lista de «mis hábitos» de mañana ya no muestra un hábito de otro rol")
    void misHabitosFiltrado() {
        restringir(habito, SOLO_MENTORES);
        reloj.fijar(Instant.parse("2026-10-13T15:00:00Z"));

        assertThat(misHabitos.consultar(aprendiz)).extracting(v -> v.habito().id()).doesNotContain(habito);
        assertThat(misHabitos.consultar(mentor)).extracting(v -> v.habito().id()).contains(habito);
    }

    @Test
    @DisplayName("Clase diaria restringida a otro rol: el aprendiz no la recibe y completarla responde que no hay, sin puntos")
    void claseDiariaRestringida() {
        HabitoId claseDiariaId = HabitoId.of(jdbc.queryForObject(
                "SELECT id FROM renaser.habitos WHERE clave_sistema = 'DAILY_CLASS'", UUID.class));
        habitosTocados.add(claseDiariaId.value());
        restringir(claseDiariaId, SOLO_MENTORES);
        reloj.fijar(Instant.parse("2026-10-13T15:00:00Z"));

        generar.generarDiaCompletoEnSuZona(aprendiz);

        assertThat(registrosDe(aprendiz, claseDiariaId)).isZero();
        // En producción la transacción la abre academy, que es quien llama (ver ClaseDiariaHabitoService).
        assertThatThrownBy(() -> transacciones.executeWithoutResult(estado -> claseDiaria.completarDeHoy(
                new CompletarClaseDiariaHabitoCommand(aprendiz,
                        "a".repeat(CompletarClaseDiariaHabitoUseCase.RESUMEN_MIN_LENGTH)))))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("eliminar: con registros se rechaza con el motivo; sin registros se borra con sus versiones")
    void eliminar() {
        restringir(habito, SOLO_MENTORES);
        HabitoId sinUso = habitoConHorario("Sin uso");
        restringir(sinUso, SOLO_MENTORES);
        habitosGenerados(aprendiz, HOY);

        assertThatThrownBy(() -> eliminar.eliminar(new EliminarHabitoCommand(admin, habito)))
                .isInstanceOf(IllegalStateException.class).hasMessage(HabitoAdminService.CON_HISTORIA);

        jdbc.update("DELETE FROM renaser.registros_habito WHERE habito_id = ?", sinUso.value());
        eliminar.eliminar(new EliminarHabitoCommand(admin, sinUso));
        Integer versiones = jdbc.queryForObject(
                "SELECT COUNT(*) FROM renaser.audiencias_habito WHERE habito_id = ?", Integer.class, sinUso.value());
        assertThat(versiones).as("las versiones se van en cascada").isZero();
    }

    private void restringir(HabitoId id, Audiencia audiencia) {
        actualizar.actualizar(new ActualizarHabitoCommand(admin, id, DETALLES, true, audiencia));
    }

    private List<HabitoId> habitosGenerados(UserId persona, LocalDate fecha) {
        generar.generar(persona, fecha);
        return jdbc.queryForList("""
                        SELECT habito_id FROM renaser.registros_habito
                        WHERE participante_id = ? AND fecha_ejecucion = ?
                        """, UUID.class, persona.value(), fecha).stream().map(HabitoId::of).toList();
    }

    private int registrosDe(UserId persona, HabitoId id) {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM renaser.registros_habito WHERE participante_id = ? AND habito_id = ?",
                Integer.class, persona.value(), id.value());
        return total == null ? 0 : total;
    }

    private UserId usuario(String rol, boolean haceElPrograma) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test", "Fixture " + rol, rol);
        if (haceElPrograma) {
            jdbc.update("""
                    INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio,
                                                                programa_activado_en, timezone)
                    VALUES (?, 15, ?, ?, 'America/Lima')
                    """, id, INICIO, Timestamp.from(Instant.parse("2026-09-26T15:00:00Z")));
        }
        usuarios.add(id);
        return UserId.of(id);
    }

    private HabitoId habitoConHorario(String titulo) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.habitos (id, titulo, categoria_clave)
                VALUES (?, ?, 'CUERPO')
                """, id, titulo + " " + id);
        jdbc.update("""
                INSERT INTO renaser.horarios_habito (habito_id, dia_inicio, tipo_dia, hora_disparo, hora_limite)
                VALUES (?, 1, 'TODOS', '06:00', '22:00')
                """, id);
        habitosTocados.add(id);
        habitosCreados.add(id);
        return HabitoId.of(id);
    }
}
