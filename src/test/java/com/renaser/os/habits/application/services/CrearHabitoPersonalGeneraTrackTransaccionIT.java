package com.renaser.os.habits.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.habito.CrearHabitoPersonalUseCase;
import com.renaser.os.habits.application.ports.in.habito.CrearHabitoPersonalUseCase.CrearHabitoPersonalCommand;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                PlantillaHabitoPersonal.CORRER, "Terminar una carrera de 5km", disparo, limite);
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

    @Test
    @DisplayName("Atomicidad: si el HorarioHabito es invalido, tampoco queda el Habito (rollback de toda la transaccion)")
    void siElHorarioEsInvalidoNoQuedaNingunHabitoHuerfano() {
        // dia_programa = 0 (DEFAULT real de participantes_programa, antes de activar el
        // programa): HorarioHabito.crear rechaza diaInicio fuera de 1..90 (domain/HorarioHabito.
        // java), y ese throw tiene que deshacer tambien el savePort.save(habito) anterior — si
        // no, queda un habito PERSONAL sin ningun horario, exactamente el bug original.
        seedParticipante(0);

        assertThatThrownBy(() -> crearUseCase.crear(comando(LocalTime.of(6, 0), LocalTime.of(22, 0))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(contarHabitosDe(participanteId))
                .as("ningun habito debe quedar persistido si su horario no pudo crearse")
                .isZero();
    }
}
