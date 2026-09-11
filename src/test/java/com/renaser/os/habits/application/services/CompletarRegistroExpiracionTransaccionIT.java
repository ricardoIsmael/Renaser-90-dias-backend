package com.renaser.os.habits.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Un habito fuera de plazo SE PUEDE REGISTRAR igual.
 *
 * <p><b>Antes no.</b> Pasada la ventana, `completar()` marcaba EXPIRADO y respondia
 * {@code 409 El habito expiro -- no se puede completar}. El dueno del proyecto lo pidio al reves
 * y tiene razon: registrar tarde es informacion, y perderla no ayuda a nadie. Quien se desperto a
 * las 10 y lo anota a las 11 HIZO el habito; lo unico que no hizo fue llegar a tiempo, y eso ya
 * se cobra donde corresponde -- {@code ResultadoOtorgamiento} devuelve 0 puntos en fase EXPIRADO.
 * Bloquear ademas el registro cobraba dos veces por la misma tardanza.
 *
 * <p><b>Que fue de C-9.</b> Esta clase nacio como regresion de C-9: "expirar y lanzar" revertia
 * la expiracion que `completar()` acababa de guardar, porque el `throw` corria dentro de la misma
 * transaccion que el `save`. Ese defecto ya no puede existir -- no queda ningun `throw` del que
 * salvar una escritura -- y con el se fue tambien el `noRollbackFor` que lo parcheaba. Se
 * conservan las pruebas contra Postgres real porque lo que ahora hay que demostrar es lo
 * contrario: que la fila queda COMPLETADA de verdad, y con cero puntos.
 *
 * <p>Requiere Postgres real: el defecto es un rollback real de una transaccion real
 * ({@code @Transactional} de Spring sobre un {@code PlatformTransactionManager} JPA
 * real) — con mocks (como en {@code RegistroServiceTest}) no hay ninguna transaccion
 * que revertir, asi que la prueba no significaria nada. Por eso se autowirea el caso
 * de uso por su interfaz publica (bean real, con el proxy de {@code @Transactional}
 * de Spring), igual que {@code ProcesarValidacionV90ServiceTransaccionIT}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CompletarRegistroExpiracionTransaccionIT {

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
                            INSERT INTO renaser.participantes_programa (usuario_id, dia_programa)
                            VALUES (:usuarioId, 5)
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

    /**
     * {@code fecha_ejecucion} en el pasado lejano: {@code VentanaEntrega.calcular} acota
     * {@code plazoEvidencia} a, como mucho, la medianoche siguiente a esa fecha — asi la
     * ventana queda vencida sin importar el reloj real de la maquina que corre el test.
     */
    private RegistroHabitoId seedRegistroPendienteMuyVencido() {
        RegistroHabitoId id = RegistroHabitoId.of(UUID.randomUUID());
        RegistroHabito registro = RegistroHabito.generar(id, participanteId, habitoId, LocalDate.of(2020, 1, 1), 5,
                TipoDia.DISCIPLINA, false, CLOCK.now());
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
    @DisplayName("Un registro vencido SE COMPLETA, y paga cero: la tardanza se cobra en puntos, no bloqueando")
    void unRegistroVencidoSeCompletaConCeroPuntos() {
        RegistroHabitoId id = seedRegistroPendienteMuyVencido();

        completarUseCase.completar(new CompletarRegistroCommand(participanteId, id, null, null));

        assertThat(estadoEnBaseDe(id))
                .as("la fila queda COMPLETADA: lo hizo, aunque tarde")
                .isEqualTo("COMPLETADO");
        assertThat(puntosEnBaseDe(id))
                .as("y paga cero, que es donde SI corresponde cobrar la tardanza")
                .isZero();
    }

    /**
     * El caso que reporto el dueno del proyecto: DESPERTAR ya estaba en EXPIRADO --lo dejo asi un
     * intento anterior o el barrido-- y no habia forma de registrarlo nunca mas.
     */
    @Test
    @DisplayName("Un registro que YA estaba EXPIRADO tambien se puede completar")
    void unRegistroYaExpiradoTambienSePuedeCompletar() {
        RegistroHabitoId id = seedRegistroPendienteMuyVencido();
        jdbcTemplate.update("""
                UPDATE renaser.registros_habito SET estado = CAST('EXPIRADO' AS renaser.estado_registro)
                WHERE id = ?
                """, id.value());

        completarUseCase.completar(new CompletarRegistroCommand(participanteId, id, null, null));

        assertThat(estadoEnBaseDe(id)).isEqualTo("COMPLETADO");
        assertThat(puntosEnBaseDe(id)).isZero();
    }

    /**
     * FALLIDO sigue cerrado, y esa es la linea. Lo marca el barrido cuando el dia CIERRA: un dia
     * cerrado es un veredicto, y dejar completarlo despues seria reescribir el pasado. Sin esta
     * prueba, "dejar registrar tarde" se podria satisfacer abriendo tambien esa puerta.
     */
    @Test
    @DisplayName("Un registro FALLIDO no se puede completar: el dia ya cerro")
    void unRegistroFallidoSigueCerrado() {
        RegistroHabitoId id = seedRegistroPendienteMuyVencido();
        jdbcTemplate.update("""
                UPDATE renaser.registros_habito SET estado = CAST('FALLIDO' AS renaser.estado_registro)
                WHERE id = ?
                """, id.value());

        assertThatThrownBy(() -> completarUseCase.completar(
                new CompletarRegistroCommand(participanteId, id, null, null)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(estadoEnBaseDe(id)).isEqualTo("FALLIDO");
    }
}
