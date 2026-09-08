package com.renaser.os.habits.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.habito.CrearHabitoPersonalUseCase;
import com.renaser.os.habits.application.ports.in.habito.CrearHabitoPersonalUseCase.CrearHabitoPersonalCommand;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.out.horario.SaveHorarioHabitoPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.PlantillaHabitoPersonal;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Cierra docs/informes/habits-eleccion-y-personales.md §3/§4.4: un habito PERSONAL sin
 * {@code HorarioHabito} nunca generaba {@code registro_habito}. Requiere Postgres real: lo que
 * se prueba es que {@code MisHabitosService.crear} guarda {@code Habito} + {@code HorarioHabito}
 * en la MISMA transaccion (atomicidad real de {@code @Transactional} sobre un
 * {@code PlatformTransactionManager} JPA real, no algo que un mock pueda demostrar — mismo
 * razonamiento que {@code CompletarRegistroExpiracionTransaccionIT}), y que
 * {@code RegistroService.generar} efectivamente genera el track del dia para ese habito
 * despues.
 *
 * <p>Antes de escribir la semilla se releyeron E-74 (transaccion activa para
 * {@code executeUpdate}, tipos de columna) y E-78 (nunca el reloj del sistema) de
 * {@code docs/BITACORA_ERRORES.md} — de ahi el seed envuelto en {@link TransactionTemplate} y el
 * uso de {@link FixedClock} en vez de {@code Instant.now()} (aunque esta prueba en particular no
 * depende de una ventana horaria, a diferencia de {@code EspirituConcurrenciaTest}).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CrearHabitoPersonalGeneraTrackTransaccionIT {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-02T10:00:00Z"));

    @Autowired
    private CrearHabitoPersonalUseCase crearUseCase;
    @Autowired
    private GenerarTracksDelDiaUseCase generarUseCase;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;
    /** Espia, no mock: los demas tests de esta clase tienen que seguir guardando de verdad. */
    @MockitoSpyBean
    private SaveHorarioHabitoPort saveHorarioPort;

    private UserId participanteId;

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra participantes_programa/habitos/horarios_habito/registros_habito.
        if (participanteId != null) {
            jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", participanteId.value());
        }
    }

    private void seedParticipante(int diaPrograma) {
        participanteId = UserId.of(UUID.randomUUID());
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                            INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                            VALUES (:id, :email, 'Fixture', 'APRENDIZ', 'ACTIVO')
                            """)
                    .setParameter("id", participanteId.value())
                    .setParameter("email", participanteId + "@renaser.test")
                    .executeUpdate();
            entityManager.createNativeQuery("""
                            INSERT INTO renaser.participantes_programa (usuario_id, dia_programa)
                            VALUES (:usuarioId, :diaPrograma)
                            """)
                    .setParameter("usuarioId", participanteId.value())
                    .setParameter("diaPrograma", diaPrograma)
                    .executeUpdate();
        });
    }

    private CrearHabitoPersonalCommand comando(LocalTime disparo, LocalTime limite) {
        return new CrearHabitoPersonalCommand(participanteId, "Correr 5km", TipoHabito.CHECKBOX, "CUERPO",
                PlantillaHabitoPersonal.CORRER, "Terminar una carrera de 5km", null, disparo, limite, null);
    }

    private long contarHabitosDe(UserId participanteId) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM renaser.habitos WHERE participante_id = ?", Long.class,
                participanteId.value());
        return total == null ? 0 : total;
    }

    @Test
    @DisplayName("Un habito personal recien creado, con horario, genera su track del dia (cierra el bug)")
    void habitoPersonalConHorarioGeneraTrackDelDia() {
        seedParticipante(23);

        Habito habito = crearUseCase.crear(comando(LocalTime.of(6, 0), LocalTime.of(22, 0)));

        // Confirma que el HorarioHabito quedo persistido junto con el Habito, en la misma
        // transaccion (no solo que el metodo no lanzo).
        Long horarios = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM renaser.horarios_habito WHERE habito_id = ?", Long.class,
                habito.id().value());
        assertThat(horarios).as("el horario debe existir junto con el habito").isEqualTo(1L);

        List<RegistroHabito> generados = generarUseCase.generar(participanteId, LocalDate.of(2026, 9, 2));

        assertThat(generados).extracting(r -> r.habitoId()).contains(habito.id());
        Long registros = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM renaser.registros_habito WHERE habito_id = ? AND participante_id = ?",
                Long.class, habito.id().value(), participanteId.value());
        assertThat(registros).as("el track del dia debe haberse generado para el habito recien creado")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("Un habito personal sin hora limite tambien genera track (limite es opcional)")
    void habitoPersonalSinHoraLimiteGeneraTrackDelDia() {
        seedParticipante(23);

        Habito habito = crearUseCase.crear(comando(LocalTime.of(6, 0), null));

        List<RegistroHabito> generados = generarUseCase.generar(participanteId, LocalDate.of(2026, 9, 2));

        assertThat(generados).extracting(r -> r.habitoId()).contains(habito.id());
    }

    /**
     * El bug de produccion del 2026-09-06 (E-137), contra Postgres real.
     *
     * <p><b>Corregido en el mismo cambio:</b> este metodo se llamaba
     * {@code siElHorarioEsInvalidoNoQuedaNingunHabitoHuerfano} y usaba {@code dia_programa = 0}
     * como forma de provocar el rechazo de {@code HorarioHabito.crear}, para demostrar el
     * rollback. Ese disparador dejo de existir: D-115 hace que el dia 0 arranque el dia 1, asi
     * que el alta ya no falla. La atomicidad NO se dejo sin cubrir — se prueba abajo, en
     * {@link #siElHorarioNoSePuedeGuardarNoQuedaNingunHabitoHuerfano}, con un disparador que
     * sigue siendo posible.
     *
     * <p>Lo que verifica: un participante en {@code dia_programa = 0} —el estado inicial de
     * TODA cuenta recien aprobada, porque el Dia 1 nunca puede ser hoy (D-66)— puede crear su
     * habito propio, y el horario que se persiste arranca el dia 1. Que la fila entre de verdad
     * importa aca y no en un test unitario: {@code horarios_habito.dia_inicio} tiene
     * {@code CHECK (dia_inicio BETWEEN 1 AND 90)} en el baseline, asi que un 0 tampoco habria
     * pasado la base.
     */
    @Test
    @DisplayName("Dia 0: el habito personal se crea y su horario arranca el dia 1 (E-137)")
    void enDia0ElHabitoSeCreaYSuHorarioArrancaElDia1() {
        seedParticipante(0);

        Habito habito = crearUseCase.crear(comando(LocalTime.of(6, 0), LocalTime.of(22, 0)));

        assertThat(contarHabitosDe(participanteId)).as("el habito debe quedar persistido").isEqualTo(1L);
        Integer diaInicio = jdbcTemplate.queryForObject(
                "SELECT dia_inicio FROM renaser.horarios_habito WHERE habito_id = ?", Integer.class,
                habito.id().value());
        assertThat(diaInicio).as("lo que se crea en dia 0 arranca el dia 1 (D-103/D-115)").isEqualTo(1);
    }

    /**
     * La garantia de atomicidad que este archivo existe para proteger: {@code Habito} y
     * {@code HorarioHabito} se guardan en la MISMA transaccion, y si el segundo paso falla no
     * puede quedar un habito PERSONAL sin horario — el bug original de
     * docs/informes/habits-personal-con-horario.md.
     *
     * <p>El fallo se inyecta en el puerto de salida y no con una entrada invalida a proposito:
     * despues de D-115 <b>ningun comando aceptado puede construir un {@code HorarioHabito}
     * invalido</b> (el dia siempre queda en 1..90, y {@code CrearHabitoPersonalCommand} ya rechaza
     * {@code horaLimite <= horaDisparo} antes de llegar al servicio). Lo que queda por cubrir es
     * justamente el fallo de infraestructura en el segundo guardado, que es lo que
     * {@code @Transactional} tiene que deshacer. Se usa un espia —no un mock— para que los otros
     * tests de esta clase sigan corriendo contra el adaptador real.
     */
    @Test
    @DisplayName("Atomicidad: si el horario no se puede guardar, tampoco queda el Habito (rollback)")
    void siElHorarioNoSePuedeGuardarNoQuedaNingunHabitoHuerfano() {
        seedParticipante(23);
        doThrow(new IllegalStateException("fallo simulado al guardar el horario"))
                .when(saveHorarioPort).save(any());

        assertThatThrownBy(() -> crearUseCase.crear(comando(LocalTime.of(6, 0), LocalTime.of(22, 0))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(contarHabitosDe(participanteId))
                .as("ningun habito debe quedar persistido si su horario no pudo guardarse")
                .isZero();
    }
}
