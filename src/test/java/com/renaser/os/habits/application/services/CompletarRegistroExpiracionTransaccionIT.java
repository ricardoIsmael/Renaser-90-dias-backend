package com.renaser.os.habits.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
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
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Un habito fuera de plazo SE PUEDE REGISTRAR igual... durante SU dia (D-259).
 *
 * <p><b>Corregido 2026-10-06 (D-259, E-573).</b> Esta clase probaba que un registro del 2020-01-01 —y uno ya
 * EXPIRADO— se completaba con 0 puntos. Lo primero sigue valiendo dentro del dia: pasada la hora, el registro sigue
 * PENDIENTE y se completa con 0 puntos (la tardanza se cobra en puntos, no bloqueando). Lo segundo dejo de valer: la
 * regla confirmada por el dueño el 2026-10-06 es «solo los del dia: un habito de un dia que ya termino no se
 * registra», y desde E-534 EXPIRADO es justamente eso. Con el reloj real de la maquina no se podia probar «mismo dia
 * pasada la hora», asi que la clase usa un reloj propio, puesto en la madrugada UTC (regla 02 §3): a las 04:30 UTC
 * todavia es el dia anterior en Lima.
 *
 * <p><b>Que fue de C-9.</b> Esta clase nacio como regresion de C-9: "expirar y lanzar" revertia la expiracion que
 * `completar()` acababa de guardar. Ese defecto ya no puede existir; lo que se prueba contra Postgres real es que la
 * fila queda COMPLETADA de verdad con cero puntos, y que un rechazo no deja escrito nada.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, CompletarRegistroExpiracionTransaccionIT.RelojConfig.class})
class CompletarRegistroExpiracionTransaccionIT {

    /** 04:30 UTC del 7 = 23:30 del 6 de octubre en Lima. */
    private static final Instant ULTIMA_MEDIA_HORA_DEL_6_EN_LIMA = Instant.parse("2026-10-07T04:30:00Z");
    /** 05:00:01 UTC del 7 = 00:00:01 del 7 en Lima: el 6 ya termino. */
    private static final Instant RECIEN_EMPEZADO_EL_7_EN_LIMA = Instant.parse("2026-10-07T05:00:01Z");
    private static final LocalDate EL_6 = LocalDate.of(2026, 10, 6);
    private static final AtomicReference<Instant> AHORA = new AtomicReference<>(ULTIMA_MEDIA_HORA_DEL_6_EN_LIMA);

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private CompletarRegistroUseCase completarUseCase;
    @Autowired
    private SaveRegistroHabitoPort saveRegistroPort;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private UserId participanteId;
    private HabitoId habitoId;

    @BeforeEach
    void seedFixtures() {
        participanteId = UserId.of(UUID.randomUUID());
        habitoId = HabitoId.of(UUID.randomUUID());

        // El EntityManager compartido exige una transaccion activa para executeUpdate;
        // @BeforeEach no la trae. La semilla commitea aparte, que es lo que la prueba
        // necesita: los datos deben existir ANTES de que corra el caso de uso.
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                            INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                            VALUES (:id, :email, 'Fixture', 'APRENDIZ', 'ACTIVO')
                            """)
                    .setParameter("id", participanteId.value())
                    .setParameter("email", participanteId + "@renaser.test")
                    .executeUpdate();
            entityManager.createNativeQuery("""
                            INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone)
                            VALUES (:usuarioId, 5, 'America/Lima')
                            """)
                    .setParameter("usuarioId", participanteId.value())
                    .executeUpdate();
            entityManager.createNativeQuery("""
                            INSERT INTO renaser.habitos (id, ambito, titulo, tipo, categoria_clave)
                            VALUES (:id, 'SISTEMA', 'Meditar', 'CHECKBOX', 'MENTE')
                            """)
                    .setParameter("id", habitoId.value())
                    .executeUpdate();
            // tipo_dia TODOS: aplica sin importar el tipo_dia real del registro (aplicaEnDia).
            entityManager.createNativeQuery("""
                            INSERT INTO renaser.horarios_habito (habito_id, dia_inicio, dia_fin, tipo_dia,
                                                                  hora_disparo, hora_limite)
                            VALUES (:habitoId, 1, NULL, 'TODOS', :horaDisparo, :horaLimite)
                            """)
                    .setParameter("habitoId", habitoId.value())
                    .setParameter("horaDisparo", LocalTime.of(6, 0))
                    .setParameter("horaLimite", LocalTime.of(8, 0))
                    .executeUpdate();
        });
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra participantes_programa / registros_habito.
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", participanteId.value());
        jdbcTemplate.update("DELETE FROM renaser.habitos WHERE id = ?", habitoId.value());
    }

    /** Un registro PENDIENTE del 6 de octubre (Lima), con horario 06:00-08:00: a las 23:30 su plazo ya paso. */
    private RegistroHabitoId seedRegistroDelSeis() {
        RegistroHabitoId id = RegistroHabitoId.of(UUID.randomUUID());
        RegistroHabito registro = RegistroHabito.generar(id, participanteId, habitoId, EL_6, 5, TipoDia.DISCIPLINA,
                false, CLOCK.now());
        saveRegistroPort.save(registro);
        return id;
    }

    private String estadoEnBaseDe(RegistroHabitoId id) {
        return jdbcTemplate.queryForObject("SELECT estado FROM renaser.registros_habito WHERE id = ?", String.class,
                id.value());
    }

    private int puntosEnBaseDe(RegistroHabitoId id) {
        Integer p = jdbcTemplate.queryForObject(
                "SELECT puntos_otorgados FROM renaser.registros_habito WHERE id = ?", Integer.class, id.value());
        return p == null ? -1 : p;
    }

    @Test
    @DisplayName("D-259: pasada la hora y dentro de su dia (23:30 de Lima) SE COMPLETA, y paga cero")
    void pasadaLaHoraDentroDelDiaSeCompletaConCeroPuntos() {
        AHORA.set(ULTIMA_MEDIA_HORA_DEL_6_EN_LIMA);
        RegistroHabitoId id = seedRegistroDelSeis();

        completarUseCase.completar(new CompletarRegistroCommand(participanteId, id, null, null));

        assertThat(estadoEnBaseDe(id)).as("lo hizo, aunque tarde").isEqualTo("COMPLETADO");
        assertThat(puntosEnBaseDe(id)).as("la tardanza se cobra en puntos").isZero();
    }

    /** Falla contra el codigo anterior a D-259: completaba el registro de un dia ya cerrado con 0 puntos. */
    @Test
    @DisplayName("D-259: terminado su dia en Lima, un registro PENDIENTE (el barrido no paso) ya no se completa")
    void terminadoSuDiaUnPendienteYaNoSeCompleta() {
        AHORA.set(RECIEN_EMPEZADO_EL_7_EN_LIMA);
        RegistroHabitoId id = seedRegistroDelSeis();

        assertThatThrownBy(() -> completarUseCase.completar(
                new CompletarRegistroCommand(participanteId, id, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Este hábito era del 6 de octubre; ese día ya cerró y no se puede registrar.");

        assertThat(estadoEnBaseDe(id)).isEqualTo("PENDIENTE");
    }

    /**
     * Falla contra el codigo anterior a D-259. Era el caso que habia reportado el dueño (DESPERTAR en EXPIRADO sin
     * forma de registrarlo): pasada la hora del mismo dia ya no queda EXPIRADO (prueba de arriba), y un EXPIRADO es de
     * un dia que termino.
     */
    @Test
    @DisplayName("D-259: un registro EXPIRADO (de un dia que ya termino) no se completa")
    void unRegistroExpiradoNoSeCompleta() {
        AHORA.set(RECIEN_EMPEZADO_EL_7_EN_LIMA);
        RegistroHabitoId id = seedRegistroDelSeis();
        jdbcTemplate.update("""
                UPDATE renaser.registros_habito SET estado = CAST('EXPIRADO' AS renaser.estado_registro)
                WHERE id = ?
                """, id.value());

        assertThatThrownBy(() -> completarUseCase.completar(
                new CompletarRegistroCommand(participanteId, id, null, null)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(estadoEnBaseDe(id)).isEqualTo("EXPIRADO");
    }

    /** FALLIDO sigue cerrado aun dentro de su dia: es el veredicto del Santuario roto. */
    @Test
    @DisplayName("Un registro FALLIDO no se puede completar")
    void unRegistroFallidoSigueCerrado() {
        AHORA.set(ULTIMA_MEDIA_HORA_DEL_6_EN_LIMA);
        RegistroHabitoId id = seedRegistroDelSeis();
        jdbcTemplate.update("""
                UPDATE renaser.registros_habito SET estado = CAST('FALLIDO' AS renaser.estado_registro)
                WHERE id = ?
                """, id.value());

        assertThatThrownBy(() -> completarUseCase.completar(
                new CompletarRegistroCommand(participanteId, id, null, null)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(estadoEnBaseDe(id)).isEqualTo("FALLIDO");
    }

    @TestConfiguration
    static class RelojConfig {
        @Bean
        @Primary
        Clock relojDeEstaPrueba() {
            return new Clock() {
                @Override
                public Instant now() {
                    return AHORA.get();
                }

                @Override
                public LocalDate today() {
                    return AHORA.get().atOffset(ZoneOffset.UTC).toLocalDate();
                }
            };
        }
    }
}
