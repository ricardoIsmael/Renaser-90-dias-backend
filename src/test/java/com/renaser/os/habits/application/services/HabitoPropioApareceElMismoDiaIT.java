package com.renaser.os.habits.application.services;

import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.desbloqueo.CambiarEstadoHabitoDelPlanUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.CambiarEstadoHabitoDelPlanUseCase.CambiarEstadoHabitoCommand;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase.ElegirHabitoCommand;
import com.renaser.os.habits.application.ports.in.habito.CrearHabitoPersonalUseCase;
import com.renaser.os.habits.application.ports.in.habito.CrearHabitoPersonalUseCase.CrearHabitoPersonalCommand;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.TrackDelDiaConCatalogo;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.PlantillaHabitoPersonal;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #76, contra Postgres real: un habito que la persona crea (o reactiva) a media manana aparece en Training ESE
 * dia, no al siguiente.
 *
 * <p><b>El escenario del bug.</b> El dia de la persona se arma una sola vez, cuando empieza (el barrido de cada hora, o la
 * primera apertura de la app): aqui se simula con {@code generarDiaCompletoEnSuZona} ANTES de crear nada, para que el dia
 * ya tenga registros —con el dia vacio la red de seguridad de {@code GET /habit-tracks/today} lo habria armado y el bug no
 * se veria—. Despues, a las 10:00 de Lima, la persona crea su habito. Contra el codigo anterior no tenia registro hoy.
 *
 * <p>Se lee por {@code consultarHoyDe}, que es lo que sirve {@code GET /habit-tracks/today} a Training, y por SQL directo
 * sobre {@code registros_habito}, para ver tambien que la UNIQUE (participante, habito, fecha) no se rompa.
 *
 * <p>Reloj fijo en un instante que ya es de dia en Lima pero del mismo dia calendario en UTC (15:00Z = 10:00): el caso del
 * reloj en la madrugada UTC (regla 02 §3) lo cubre {@code GenerarElDeHoyDeUnHabitoTest} sin base de datos.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class HabitoPropioApareceElMismoDiaIT {

    /** Martes 8/9/2026, 10:00 en Lima. */
    private static final Instant MEDIA_MANANA = Instant.parse("2026-09-08T15:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 9, 8);
    private static final int DIA_DEL_PROGRAMA = 10;

    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private CrearHabitoPersonalUseCase crearUseCase;
    @Autowired
    private GenerarTracksDelDiaUseCase generarUseCase;
    @Autowired
    private ConsultarTracksDelDiaConCatalogoUseCase tracksDeHoy;
    @Autowired
    private CambiarEstadoHabitoDelPlanUseCase cambiarEstadoUseCase;
    @Autowired
    private ElegirHabitoUseCase elegirUseCase;
    @Autowired
    private LoadHabitoPort loadHabitoPort;
    @Autowired
    private JdbcTemplate jdbc;

    private UserId persona;

    @BeforeEach
    void sembrar() {
        reloj.fijar(MEDIA_MANANA);
        persona = UserId.of(UUID.randomUUID());
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Fixture #76', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                """, persona.value(), persona + "@renaser.test");
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone) VALUES (?, ?, 'America/Lima')",
                persona.value(), DIA_DEL_PROGRAMA);
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra participantes_programa, habitos, horarios_habito, desbloqueos y registros.
        jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", persona.value());
    }

    /** Lo que ya hizo el barrido de esa manana: el dia armado con los habitos del programa. */
    private void armarElDiaDeEstaManana() {
        assertThat(generarUseCase.generarDiaCompletoEnSuZona(persona))
                .as("precondicion: el dia queda armado antes de crear nada").isNotEmpty();
    }

    private Habito crear(LocalTime disparo, LocalTime limite, Set<DayOfWeek> dias) {
        return crearUseCase.crear(new CrearHabitoPersonalCommand(persona, "Leer 20 minutos", TipoHabito.CHECKBOX, "MENTE",
                PlantillaHabitoPersonal.OTRO, "meta", null, disparo, limite, dias));
    }

    private long registrosDeHoy(HabitoId habito) {
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM renaser.registros_habito
                WHERE participante_id = ? AND habito_id = ? AND fecha_ejecucion = ?
                """, Long.class, persona.value(), habito.value(), HOY);
        return total == null ? 0 : total;
    }

    private List<TrackDelDiaConCatalogo> loQueVeTrainingHoy() {
        return tracksDeHoy.consultarHoyDe(persona);
    }

    private boolean trainingLoMuestra(Habito habito) {
        return loQueVeTrainingHoy().stream().anyMatch(t -> t.registro().habitoId().equals(habito.id()));
    }

    // ───────────────────────────── crear ─────────────────────────────

    /** Falla contra el codigo anterior: el habito creado a las 10:00 no tenia registro hasta el dia siguiente. */
    @Test
    @DisplayName("Un habito propio creado a media manana aparece hoy en lo que lee Training, pendiente y con su hora")
    void creadoAMediaMananaApareceHoy() {
        armarElDiaDeEstaManana();

        Habito habito = crear(LocalTime.of(18, 0), LocalTime.of(22, 0), null);

        assertThat(registrosDeHoy(habito.id())).isEqualTo(1L);
        TrackDelDiaConCatalogo track = loQueVeTrainingHoy().stream()
                .filter(t -> t.registro().habitoId().equals(habito.id())).findFirst().orElseThrow();
        assertThat(track.tituloHabito()).isEqualTo("Leer 20 minutos");
        assertThat(track.registro().fechaEjecucion()).isEqualTo(HOY);
        assertThat(track.registro().diaPrograma()).as("el mismo snapshot que arma el barrido").isEqualTo(DIA_DEL_PROGRAMA);
        assertThat(track.registro().estado().name()).isEqualTo("PENDIENTE");
        assertThat(track.horaDisparo()).isEqualTo(LocalTime.of(18, 0));
        assertThat(track.horaLimite()).isEqualTo(LocalTime.of(22, 0));
        assertThat(track.puntosEnJuego()).as("todavia puede cumplirlo a tiempo: con sus puntos en juego").isNotNull();
    }

    /** D-259: aunque la hora ya paso, se registra durante su dia; solo bajan los puntos. */
    @Test
    @DisplayName("D-259: con la hora limite de hoy ya pasada igual aparece y se puede registrar")
    void conLaHoraYaPasadaApareceIgual() {
        armarElDiaDeEstaManana();

        Habito habito = crear(LocalTime.of(6, 0), LocalTime.of(8, 0), null);

        assertThat(trainingLoMuestra(habito)).isTrue();
    }

    /** El barrido de las horas siguientes (o la app al abrirse) vuelve a armar el dia: no puede duplicarlo ni fallar. */
    @Test
    @DisplayName("No se duplica: el armado del dia que corre despues deja un solo registro")
    void elArmadoDelDiaQueCorreDespuesNoLoDuplica() {
        armarElDiaDeEstaManana();
        Habito habito = crear(LocalTime.of(18, 0), null, null);

        List<RegistroHabito> nuevos = generarUseCase.generarDiaCompletoEnSuZona(persona);
        List<RegistroHabito> deLaApp = generarUseCase.generarDisponiblesAhora(persona);
        generarUseCase.generar(persona, HOY);
        generarUseCase.generarElDeHoyDe(persona, List.of(habito.id()));

        assertThat(nuevos).extracting(RegistroHabito::habitoId).doesNotContain(habito.id());
        assertThat(deLaApp).extracting(RegistroHabito::habitoId).doesNotContain(habito.id());
        assertThat(registrosDeHoy(habito.id())).isEqualTo(1L);
    }

    /**
     * Si el barrido de esa hora fallo y el habito se crea antes de abrir Training (por ejemplo, desde SER), un registro suelto
     * haria que el dia pareciera armado: la red de seguridad de {@code GET /habit-tracks/today} solo arma un dia VACIO.
     * El dia se arma entero y el habito nuevo sale una sola vez.
     */
    @Test
    @DisplayName("Si el dia ni se armo, crear el habito arma el dia entero: no queda solo el habito nuevo")
    void conElDiaSinArmarSeArmaEntero() {
        Habito habito = crear(LocalTime.of(18, 0), null, null);

        List<TrackDelDiaConCatalogo> hoy = loQueVeTrainingHoy();

        assertThat(hoy).as("el habito nuevo y los del programa").hasSizeGreaterThan(1);
        assertThat(hoy).filteredOn(t -> t.registro().habitoId().equals(habito.id())).hasSize(1);
        assertThat(registrosDeHoy(habito.id())).isEqualTo(1L);
    }

    @Test
    @DisplayName("Dia 0: el programa no empezo, no hay registro de hoy; el dia 1 el armado del dia lo crea")
    void enElDiaCeroNoApareceHoy() {
        jdbc.update("UPDATE renaser.participantes_programa SET dia_programa = 0 WHERE usuario_id = ?", persona.value());

        Habito habito = crear(LocalTime.of(18, 0), null, null);

        assertThat(registrosDeHoy(habito.id())).isZero();
        assertThat(trainingLoMuestra(habito)).isFalse();

        jdbc.update("UPDATE renaser.participantes_programa SET dia_programa = 1 WHERE usuario_id = ?", persona.value());
        assertThat(generarUseCase.generarDiaCompletoEnSuZona(persona)).extracting(RegistroHabito::habitoId)
                .as("el dia 1 el barrido de siempre lo arma").contains(habito.id());
    }

    @Test
    @DisplayName("Un dia que no le toca (hoy es martes y lo hace lunes, miercoles y viernes) no tiene registro")
    void unDiaQueNoLeTocaNoApareceHoy() {
        armarElDiaDeEstaManana();

        Habito habito = crear(LocalTime.of(18, 0), null,
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));

        assertThat(registrosDeHoy(habito.id())).isZero();
        assertThat(trainingLoMuestra(habito)).isFalse();
    }

    @Test
    @DisplayName("Un dia que si le toca (hoy martes entre sus dias) aparece hoy")
    void unDiaQueSiLeTocaApareceHoy() {
        armarElDiaDeEstaManana();

        Habito habito = crear(LocalTime.of(18, 0), null, EnumSet.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY));

        assertThat(registrosDeHoy(habito.id())).isEqualTo(1L);
        assertThat(trainingLoMuestra(habito)).isTrue();
    }

    // ───────────────────────────── pausa y reactivar ─────────────────────────────

    /**
     * Pausar borra el registro pendiente de hoy; reactivar el mismo dia lo devuelve. Contra el codigo anterior el habito
     * quedaba «activo» pero sin registro hasta el dia siguiente.
     */
    @Test
    @DisplayName("Pausar un habito propio lo saca de hoy, y reactivarlo el mismo dia lo devuelve, sin duplicar")
    void reactivarElMismoDiaLoDevuelve() {
        armarElDiaDeEstaManana();
        Habito habito = crear(LocalTime.of(18, 0), null, null);
        elegirUseCase.elegir(new ElegirHabitoCommand(persona, habito.id(), null));

        cambiarEstadoUseCase.cambiarEstado(new CambiarEstadoHabitoCommand(persona, habito.id(), false, null));
        assertThat(registrosDeHoy(habito.id())).as("pausado: sin registro hoy").isZero();
        generarUseCase.generarElDeHoyDe(persona, List.of(habito.id()));
        assertThat(registrosDeHoy(habito.id())).as("pausado sigue sin registro, aunque se pida").isZero();

        cambiarEstadoUseCase.cambiarEstado(new CambiarEstadoHabitoCommand(persona, habito.id(), true, null));

        assertThat(registrosDeHoy(habito.id())).isEqualTo(1L);
        assertThat(trainingLoMuestra(habito)).isTrue();
        cambiarEstadoUseCase.cambiarEstado(new CambiarEstadoHabitoCommand(persona, habito.id(), true, null));
        assertThat(registrosDeHoy(habito.id())).as("reactivar dos veces no duplica").isEqualTo(1L);
    }

    @Test
    @DisplayName("Una pausa «hasta que yo lo reactive» de varios dias: al reactivar vuelve hoy, no manana")
    void reactivarUnaPausaLargaLoDevuelveHoy() {
        armarElDiaDeEstaManana();
        Habito habito = crear(LocalTime.of(18, 0), null, null);
        elegirUseCase.elegir(new ElegirHabitoCommand(persona, habito.id(), null));
        cambiarEstadoUseCase.cambiarEstado(new CambiarEstadoHabitoCommand(persona, habito.id(), false, HOY.plusDays(5)));
        assertThat(registrosDeHoy(habito.id())).isZero();

        cambiarEstadoUseCase.cambiarEstado(new CambiarEstadoHabitoCommand(persona, habito.id(), true, null));

        assertThat(trainingLoMuestra(habito)).isTrue();
    }

    /** El mismo caso con un habito del programa: se pausa a media manana y se vuelve a prender. */
    @Test
    @DisplayName("Un habito del programa que se pauso y se reactivo el mismo dia vuelve a estar hoy")
    void reactivarUnHabitoDelProgramaLoDevuelve() {
        armarElDiaDeEstaManana();
        Habito delPrograma = loQueVeTrainingHoy().stream()
                .map(t -> loadHabitoPort.byId(t.registro().habitoId()).orElseThrow())
                .filter(h -> h.desactivable() && h.esDeSistema())
                .findFirst().orElseThrow();
        elegirUseCase.elegir(new ElegirHabitoCommand(persona, delPrograma.id(), null));
        cambiarEstadoUseCase.cambiarEstado(new CambiarEstadoHabitoCommand(persona, delPrograma.id(), false, null));
        assertThat(registrosDeHoy(delPrograma.id())).isZero();

        cambiarEstadoUseCase.cambiarEstado(new CambiarEstadoHabitoCommand(persona, delPrograma.id(), true, null));

        assertThat(registrosDeHoy(delPrograma.id())).isEqualTo(1L);
    }
}
