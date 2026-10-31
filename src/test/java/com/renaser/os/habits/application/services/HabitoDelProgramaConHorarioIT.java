package com.renaser.os.habits.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.habitoadmin.CrearHabitoUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.CrearHabitoUseCase.CrearHabitoCommand;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.habits.domain.model.audiencia.Audiencia;
import com.renaser.os.habits.domain.model.habito.DetallesHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-659 (issue #44) contra Postgres real: un habito creado desde Administracion -> «Habitos del programa» tiene su
 * horario y le aparece a la gente, y la migracion V123 completa los que quedaron sin horario.
 *
 * <p>Por que esta prueba no existia: todas las que arman un habito de catalogo lo hacen con {@code INSERT} directo en
 * {@code habitos} Y en {@code horarios_habito} (ver {@code HabitosPorRolIT.habitoConHorario}). El fixture ya traia la
 * fila que el alta del panel olvidaba escribir, y por eso ninguna prueba veia el bug. Aqui el habito se crea por el
 * caso de uso real y la fila de horario no se siembra nunca a mano.
 *
 * <p>Sin reloj propio: la generacion recibe la fecha por parametro y lo que se verifica no depende de la hora del
 * dia, sino de que haya o no una fila de horario que cubra el dia. Se recorren siete fechas seguidas, asi que entran
 * un domingo y los seis dias de disciplina.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class HabitoDelProgramaConHorarioIT {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final String MIGRACION = "db/migration/V123__horario_de_los_habitos_del_programa_sin_horario.sql";
    private static final DetallesHabito DETALLES = new DetallesHabito("desc", "CUERPO", ExigenciaEvidencia.OPCIONAL,
            false, false);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private TransactionTemplate transacciones;
    @Autowired
    private Clock clock;
    @Autowired
    private CrearHabitoUseCase crear;
    @Autowired
    private GenerarTracksDelDiaUseCase generar;

    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> habitos = new ArrayList<>();
    private UserId admin;
    private UserId aprendiz;
    private LocalDate hoy;

    @BeforeEach
    void preparar() {
        hoy = clock.now().atZone(LIMA).toLocalDate();
        admin = usuario("ADMIN", false);
        aprendiz = usuario("APRENDIZ", true);
    }

    @AfterEach
    void limpiar() {
        usuarios.forEach(id -> {
            jdbc.update("DELETE FROM renaser.registros_habito WHERE participante_id = ?", id);
            try {
                jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id);
            } catch (DataAccessException quedaReferenciado) {
                // La base de la suite es descartable.
            }
        });
        // El horario muere en cascada con el habito (V1); las versiones de audiencia tambien.
        habitos.forEach(id -> jdbc.update("DELETE FROM renaser.habitos WHERE id = ?", id));
    }

    @Test
    @DisplayName("un hábito creado desde el panel, sin hora, tiene su horario y se genera todos los días para la persona")
    void habitoSinHoraSeGeneraTodosLosDias() {
        HabitoId habito = crearDesdeElPanel("Revisión del grupo", null);

        assertThat(horariosDe(habito)).as("una sola fila, desde el Día 1, sin fin, todos los días y sin horas")
                .containsExactly("1|null|TODOS|null|null");
        for (int i = 0; i < 7; i++) {
            LocalDate fecha = hoy.plusDays(i);
            assertThat(habitosGenerados(aprendiz, fecha)).as("el " + fecha + " (" + fecha.getDayOfWeek() + ")")
                    .contains(habito);
        }
    }

    @Test
    @DisplayName("con hora, la hora queda en su horario y el hábito también se genera")
    void habitoConHoraGuardaLaHora() {
        HabitoId habito = crearDesdeElPanel("Estiramientos", LocalTime.of(7, 30));

        LocalTime hora = jdbc.queryForObject("SELECT hora_disparo FROM renaser.horarios_habito WHERE habito_id = ?",
                java.sql.Time.class, habito.value()).toLocalTime();
        assertThat(hora).isEqualTo(LocalTime.of(7, 30));
        assertThat(habitosGenerados(aprendiz, hoy)).contains(habito);
    }

    @Test
    @DisplayName("la causa: un hábito activo sin ninguna fila de horario no se genera para nadie")
    void sinHorarioNoSeGenera() {
        HabitoId sinHorario = habitoSembrado("Sembrado sin horario", true, false);

        assertThat(habitosGenerados(aprendiz, hoy)).doesNotContain(sinHorario);
    }

    @Test
    @DisplayName("V123 completa los hábitos del programa activos y sin horario, y nada más")
    void laMigracionCompletaSoloLosActivosSinHorario() {
        transacciones.executeWithoutResult(estado -> {
            HabitoId activoSinHorario = habitoSembrado("Activo sin horario", true, false);
            HabitoId inactivoSinHorario = habitoSembrado("Inactivo sin horario", false, false);
            HabitoId activoConHorario = habitoSembrado("Activo con horario", true, true);
            HabitoId personalSinHorario = habitoPersonalSembrado();

            correrMigracion();

            assertThat(horariosDe(activoSinHorario)).as("completado: Día 1, sin fin, todos los días, sin horas")
                    .containsExactly("1|null|TODOS|null|null");
            assertThat(horariosDe(inactivoSinHorario)).as("un inactivo no cambia (el pedido fue solo los activos)")
                    .isEmpty();
            assertThat(horariosDe(personalSinHorario)).as("un personal crea su horario al nacer: no es de esta").isEmpty();
            assertThat(horariosDe(activoConHorario)).as("el horario que ya existe no se toca")
                    .containsExactly("3|null|DOMINGO|06:00:00|22:00:00");

            correrMigracion();

            assertThat(horariosDe(activoSinHorario)).as("idempotente: una segunda corrida no suma otra fila")
                    .hasSize(1);
            estado.setRollbackOnly();
        });
    }

    private void correrMigracion() {
        Connection conexion = DataSourceUtils.getConnection(dataSource);
        try {
            ScriptUtils.executeSqlScript(conexion, new ClassPathResource(MIGRACION));
        } finally {
            DataSourceUtils.releaseConnection(conexion, dataSource);
        }
    }

    private List<String> horariosDe(HabitoId habito) {
        return jdbc.query("""
                SELECT dia_inicio || '|' || COALESCE(dia_fin::text, 'null') || '|' || tipo_dia::text || '|'
                       || COALESCE(hora_disparo::text, 'null') || '|' || COALESCE(hora_limite::text, 'null')
                FROM renaser.horarios_habito WHERE habito_id = ?
                """, (rs, n) -> rs.getString(1), habito.value());
    }

    private HabitoId crearDesdeElPanel(String titulo, LocalTime hora) {
        String unico = titulo + " " + UUID.randomUUID();
        HabitoId id = crear.crear(new CrearHabitoCommand(admin, unico, TipoHabito.CHECKBOX, DETALLES,
                Audiencia.PARA_TODOS, hora)).habito().id();
        habitos.add(id.value());
        return id;
    }

    private List<HabitoId> habitosGenerados(UserId persona, LocalDate fecha) {
        generar.generar(persona, fecha);
        return jdbc.queryForList("""
                SELECT habito_id FROM renaser.registros_habito
                WHERE participante_id = ? AND fecha_ejecucion = ?
                """, UUID.class, persona.value(), fecha).stream().map(HabitoId::of).toList();
    }

    /** Un hábito del programa armado a mano, tal como lo dejó el alta del panel antes del arreglo. */
    private HabitoId habitoSembrado(String titulo, boolean activo, boolean conHorario) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.habitos (id, titulo, categoria_clave, activo) VALUES (?, ?, 'CUERPO', ?)",
                id, titulo + " " + id, activo);
        if (conHorario) {
            jdbc.update("""
                    INSERT INTO renaser.horarios_habito (habito_id, dia_inicio, tipo_dia, hora_disparo, hora_limite)
                    VALUES (?, 3, 'DOMINGO', '06:00', '22:00')
                    """, id);
        }
        habitos.add(id);
        return HabitoId.of(id);
    }

    private HabitoId habitoPersonalSembrado() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.habitos (id, ambito, participante_id, titulo, categoria_clave)
                VALUES (?, 'PERSONAL', ?, ?, 'CUERPO')
                """, id, aprendiz.value(), "Personal " + id);
        habitos.add(id);
        return HabitoId.of(id);
    }

    private UserId usuario(String rol, boolean haceElPrograma) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test", "Fixture " + rol, rol);
        if (haceElPrograma) {
            // Coherente (regla 03): el Día 15 en Lima es hoy menos 14 días.
            jdbc.update("""
                    INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio,
                                                                programa_activado_en, timezone)
                    VALUES (?, 15, ?, ?, 'America/Lima')
                    """, id, hoy.minusDays(14), Timestamp.from(Instant.parse("2026-09-26T15:00:00Z")));
        }
        usuarios.add(id);
        return UserId.of(id);
    }
}
