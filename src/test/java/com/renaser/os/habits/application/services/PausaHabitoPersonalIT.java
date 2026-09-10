package com.renaser.os.habits.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.desbloqueo.CambiarEstadoHabitoDelPlanUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.CambiarEstadoHabitoDelPlanUseCase.CambiarEstadoHabitoCommand;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase.ElegirHabitoCommand;
import com.renaser.os.habits.application.ports.in.habito.CrearHabitoPersonalUseCase;
import com.renaser.os.habits.application.ports.in.habito.CrearHabitoPersonalUseCase.CrearHabitoPersonalCommand;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.PlantillaHabitoPersonal;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-138 contra Postgres real: el interruptor ACTIVO/PAUSADO tiene que funcionar sobre un habito
 * PERSONAL, no solo sobre los del catalogo.
 *
 * <p><b>Por que hace falta la base de verdad y no alcanza un test con mocks.</b> El arreglo mete
 * una fila de {@code desbloqueos_habito} cuyo {@code habito_id} apunta a un habito
 * {@code ambito = 'PERSONAL'}, algo que hasta ahora nunca habia pasado: la tabla se penso para el
 * catalogo compartido. Lo que solo la base puede responder es si esa fila entra —FK contra
 * {@code habitos(id)}, {@code CHECK (dia_desbloqueo BETWEEN 1 AND 90)} del baseline, y
 * {@code desbloqueos_pausa_hasta_requiere_pausa} de V31—, y si la generacion del dia la respeta.
 *
 * <p>Se ejercita la SECUENCIA EXACTA del movil ({@code PlanScreen.aplicarEstadoHabito}, D-99):
 * {@code PUT /habit-unlocks/{id}} ({@link ElegirHabitoUseCase}) y despues
 * {@code PATCH /habit-unlocks/{id}} ({@link CambiarEstadoHabitoDelPlanUseCase}). Contra el codigo
 * viejo el primer paso terminaba en
 * {@code 400 "Solo se eligen habitos del catalogo, no habitos personales"}.
 *
 * <p>Semilla envuelta en {@link TransactionTemplate} por E-74 (una native query necesita
 * transaccion activa), igual que {@code CrearHabitoPersonalGeneraTrackTransaccionIT}.
 *
 * <p><b>Reloj fijo, agregado 2026-09-10.</b> Esta prueba se puso roja sola, sin que nadie tocara
 * el codigo: las tres fechas eran constantes que estaban en el futuro cuando se escribio, y el
 * calendario las alcanzo. Desde el arreglo de E-91 (2026-09-07) una pausa es un rango que
 * <b>arranca cuando se toca el boton</b> y no se aplica hacia atras — asi que al llegar el
 * 2026-09-10, pausar "hoy" y preguntar por el 8 dejo de significar "dentro de la pausa" y paso a
 * significar "dos dias antes de que existiera". La produccion respondia bien; la que mentia era
 * la prueba.
 *
 * <p>La semantica correcta ya tenia su prueba: {@code DesbloqueoHabitoPausaTest
 * .unaPausaNoApagaLosDiasANTERIORESaHaberlaPuesto}. O sea que estas dos afirmaban lo contrario
 * que aquella, y convivieron en verde solo mientras el calendario dejo que las dos parecieran
 * ciertas.
 *
 * <p>Se congela {@link #AHORA} en vez de mover las fechas a {@code hoy.plusDays(n)}: con fechas
 * relativas el mismo fallo vuelve en la ventana de medianoche —el {@code hoy} del test y el
 * instante de la pausa caen en dias distintos— y ademas el dia de la semana cambiaria en cada
 * ejecucion. Con el reloj fijo, {@code pausadoEn} vale exactamente {@link #AHORA} y las tres
 * fechas vuelven a decir lo que sus nombres prometen, para siempre. Mismo patron que
 * {@code DesbloqueoHabitoConcurrenciaTest}.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, PausaHabitoPersonalIT.FixedClockConfig.class})
class PausaHabitoPersonalIT {

    private static final int DIA_PROGRAMA = 10;

    /**
     * El instante en que se pausa. En {@code America/Lima} (UTC-5, el default de
     * {@code participantes_programa.timezone} y la zona con la que la produccion resuelve la
     * pausa) son las 10:00 del martes 8 — o sea, el mismo dia del calendario que
     * {@link #DIA_DENTRO_DE_LA_PAUSA}, que es justo lo que el nombre de esa constante afirma.
     *
     * <p>La hora del medio del dia no es casual: con 15:00Z, la conversion a Lima cae dentro del
     * mismo dia sin importar el horario de verano de ningun otro lado. Con 02:00Z caeria en el
     * dia anterior y la prueba volveria a mentir, esta vez por una razon mas dificil de ver.
     */
    private static final Instant AHORA = Instant.parse("2026-09-08T15:00:00Z");
    private static final String ZONA_DEL_PARTICIPANTE = "America/Lima";

    /** Un martes: {@code TipoDia.delDia} da DISCIPLINA, y un horario {@code TODOS} aplica igual. */
    private static final LocalDate DIA_DENTRO_DE_LA_PAUSA = LocalDate.of(2026, 9, 8);
    private static final LocalDate ULTIMO_DIA_DE_LA_PAUSA = LocalDate.of(2026, 9, 9);
    private static final LocalDate DIA_SIGUIENTE_A_LA_PAUSA = LocalDate.of(2026, 9, 10);

    @Autowired
    private CrearHabitoPersonalUseCase crearUseCase;
    @Autowired
    private ElegirHabitoUseCase elegirUseCase;
    @Autowired
    private CambiarEstadoHabitoDelPlanUseCase cambiarEstadoUseCase;
    @Autowired
    private GenerarTracksDelDiaUseCase generarUseCase;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private UserId participanteId;
    private Habito habitoPersonal;

    @BeforeEach
    void seedParticipanteConHabitoPropio() {
        participanteId = UserId.of(UUID.randomUUID());
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                            INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                            VALUES (:id, :email, 'Fixture', 'APRENDIZ', 'ACTIVO')
                            """)
                    .setParameter("id", participanteId.value())
                    .setParameter("email", participanteId + "@renaser.test")
                    .executeUpdate();
            // La zona va EXPLICITA aunque coincida con el default de la columna: es la que
            // convierte `pausado_en` a dia del calendario, o sea de la que dependen las tres
            // fechas de arriba. Dejarla implicita esconde esa dependencia justo donde ya rompio.
            entityManager.createNativeQuery("""
                            INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone)
                            VALUES (:usuarioId, :diaPrograma, :zona)
                            """)
                    .setParameter("usuarioId", participanteId.value())
                    .setParameter("diaPrograma", DIA_PROGRAMA)
                    .setParameter("zona", ZONA_DEL_PARTICIPANTE)
                    .executeUpdate();
        });
        habitoPersonal = crearUseCase.crear(new CrearHabitoPersonalCommand(participanteId, "Correr 5km",
                TipoHabito.CHECKBOX, "CUERPO", PlantillaHabitoPersonal.CORRER, "Terminar una carrera",
                null, LocalTime.of(6, 0), LocalTime.of(22, 0), null));
    }

    @AfterEach
    void limpiar() {
        if (participanteId != null) {
            jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", participanteId.value());
        }
    }

    @Test
    @DisplayName("El habito propio entra en desbloqueos_habito: la fila existe en la base (E-138)")
    void elHabitoPersonalPropioEntraEnElPlan() {
        DesbloqueoHabito desbloqueo = elegirUseCase.elegir(
                new ElegirHabitoCommand(participanteId, habitoPersonal.id(), null));

        assertThat(desbloqueo.diaDesbloqueo()).isEqualTo(DIA_PROGRAMA);
        assertThat(contarDesbloqueos()).as("la FK y el CHECK del baseline aceptan un habito PERSONAL")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("Pausar un habito propio hasta una fecha lo saca de la generacion, y vuelve solo despues")
    void pausarElHabitoPersonalLoSacaDelDiaYLuegoVuelveSolo() {
        elegirUseCase.elegir(new ElegirHabitoCommand(participanteId, habitoPersonal.id(), null));

        DesbloqueoHabito pausado = cambiarEstadoUseCase.cambiarEstado(
                new CambiarEstadoHabitoCommand(participanteId, habitoPersonal.id(), false, ULTIMO_DIA_DE_LA_PAUSA));

        assertThat(pausado.estaPausado()).isTrue();
        assertThat(pausadoHastaEnLaBase()).isEqualTo(ULTIMO_DIA_DE_LA_PAUSA);

        assertThat(generarPara(DIA_DENTRO_DE_LA_PAUSA))
                .as("un habito pausado no genera track ese dia")
                .doesNotContain(habitoPersonal.id().value());
        // La reanudacion se DERIVA de la fecha: nadie toca nada entre estas dos lineas.
        assertThat(generarPara(DIA_SIGUIENTE_A_LA_PAUSA))
                .as("pasado el ultimo dia de la pausa el habito vuelve solo (V31)")
                .contains(habitoPersonal.id().value());
    }

    @Test
    @DisplayName("Reactivar un habito propio pausado lo devuelve a la generacion del mismo dia")
    void reactivarElHabitoPersonalLoDevuelveAlDia() {
        elegirUseCase.elegir(new ElegirHabitoCommand(participanteId, habitoPersonal.id(), null));
        cambiarEstadoUseCase.cambiarEstado(
                new CambiarEstadoHabitoCommand(participanteId, habitoPersonal.id(), false, null));
        assertThat(generarPara(DIA_DENTRO_DE_LA_PAUSA)).doesNotContain(habitoPersonal.id().value());

        DesbloqueoHabito reactivado = cambiarEstadoUseCase.cambiarEstado(
                new CambiarEstadoHabitoCommand(participanteId, habitoPersonal.id(), true, null));

        assertThat(reactivado.estaPausado()).isFalse();
        assertThat(pausadoHastaEnLaBase()).isNull();
        assertThat(generarPara(DIA_SIGUIENTE_A_LA_PAUSA)).contains(habitoPersonal.id().value());
    }

    private List<UUID> generarPara(LocalDate fecha) {
        return generarUseCase.generar(participanteId, fecha).stream()
                .map(RegistroHabito::habitoId)
                .map(id -> id.value())
                .toList();
    }

    private long contarDesbloqueos() {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM renaser.desbloqueos_habito WHERE participante_id = ? AND habito_id = ?",
                Long.class, participanteId.value(), habitoPersonal.id().value());
        return total == null ? 0 : total;
    }

    private LocalDate pausadoHastaEnLaBase() {
        return jdbcTemplate.queryForObject(
                "SELECT pausado_hasta FROM renaser.desbloqueos_habito WHERE participante_id = ? AND habito_id = ?",
                LocalDate.class, participanteId.value(), habitoPersonal.id().value());
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock relojFijo() {
            return FixedClock.at(AHORA);
        }
    }
}
