package com.renaser.os.habits.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase.ElegirHabitoCommand;
import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.renaser.os.shared.domain.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresion de idempotencia para {@link ElegirHabitoUseCase} (docs/informes/habits-eleccion-y-personales.md
 * §2): elegir el MISMO habito de catalogo dos o mas veces (doble tap, reintento de red, dos
 * pestañas) nunca debe fallar ni duplicar la fila de {@code desbloqueos_habito} — a diferencia
 * de {@code RocaDiariaConcurrenciaTest} (C-2), aca TODOS los intentos concurrentes deben
 * terminar en exito (INSERT ... ON CONFLICT DO NOTHING, sin bloqueo pesimista: no hay
 * lectura-modificacion-escritura que proteger, ver javadoc de {@code SaveDesbloqueoHabitoPort}).
 *
 * <p>NO verificado con {@code ./mvnw} en esta pasada (regla del encargo) — requiere Postgres
 * real via Testcontainers, igual que su hermana de {@code rocks}. Seed con {@link JdbcTemplate}
 * (auto-commit por sentencia) para que los hilos del {@code ExecutorService} (cada uno con su
 * propia conexion) vean los datos ya commiteados — mismo motivo documentado en E-74
 * (docs/BITACORA_ERRORES.md): la transaccion del test nunca hace commit si se sembrara con el
 * {@code EntityManager} compartido dentro de un {@code @Transactional} de clase.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, DesbloqueoHabitoConcurrenciaTest.FixedClockConfig.class})
class DesbloqueoHabitoConcurrenciaTest {

    private static final Instant AHORA = Instant.parse("2026-08-26T10:00:00Z");
    private static final int INTENTOS_CONCURRENTES = 6;

    @Autowired
    private ElegirHabitoUseCase elegirUseCase;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserId participanteId;
    private HabitoId habitoId;

    @BeforeEach
    void seedParticipanteYHabito() {
        participanteId = UserId.of(UUID.randomUUID());
        habitoId = HabitoId.of(UUID.randomUUID());

        jdbcTemplate.update("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (?, ?, 'Fixture', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """,
                participanteId.value(), participanteId + "@renaser.test");
        jdbcTemplate.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa) VALUES (?, 10)",
                participanteId.value());
        jdbcTemplate.update("""
                        INSERT INTO renaser.habitos (id, titulo, categoria_clave)
                        VALUES (?, 'Fixture habito de catalogo', 'MENTE')
                        """,
                habitoId.value());
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra participantes_programa / desbloqueos_habito.
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", participanteId.value());
        jdbcTemplate.update("DELETE FROM renaser.habitos WHERE id = ?", habitoId.value());
    }

    @Test
    @DisplayName("elegir un habito de catalogo crea el desbloqueo con el dia de programa actual")
    void eligeUnHabitoNormal() {
        DesbloqueoHabito resultado = elegirUseCase.elegir(new ElegirHabitoCommand(participanteId, habitoId, null));

        assertThat(resultado.participanteId()).isEqualTo(participanteId);
        assertThat(resultado.habitoId()).isEqualTo(habitoId);
        assertThat(resultado.diaDesbloqueo()).isEqualTo(10);
        assertThat(contarDesbloqueos()).isEqualTo(1);
    }

    @Test
    @DisplayName("elegir el mismo habito dos veces seguidas no duplica la fila")
    void elegirDosVecesSeguidasNoDuplica() {
        elegirUseCase.elegir(new ElegirHabitoCommand(participanteId, habitoId, null));
        elegirUseCase.elegir(new ElegirHabitoCommand(participanteId, habitoId, null));

        assertThat(contarDesbloqueos()).isEqualTo(1);
    }

    @Test
    @DisplayName("N elecciones concurrentes del mismo habito -> una sola fila, TODAS exitosas (sin 409)")
    void eleccionesConcurrentesDelMismoHabitoNoDuplicanNiFallan() throws InterruptedException, ExecutionException {
        ExecutorService pool = Executors.newFixedThreadPool(INTENTOS_CONCURRENTES);
        List<Future<DesbloqueoHabito>> resultados;
        try {
            List<Callable<DesbloqueoHabito>> intentos = IntStream.range(0, INTENTOS_CONCURRENTES)
                    .<Callable<DesbloqueoHabito>>mapToObj(i -> () ->
                            elegirUseCase.elegir(new ElegirHabitoCommand(participanteId, habitoId, null)))
                    .toList();
            resultados = pool.invokeAll(intentos, 30, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }

        // A diferencia de C-2 (rocks), NINGUN intento debe lanzar: elegir es "agregar a un
        // conjunto", no "mutar un estado ya existente" — no hay perdedor de la carrera.
        for (Future<DesbloqueoHabito> resultado : resultados) {
            assertThat(resultado.get()).as("ningun intento concurrente debe fallar").isNotNull();
        }
        assertThat(contarDesbloqueos()).as("una sola fila pese a %s intentos concurrentes", INTENTOS_CONCURRENTES)
                .isEqualTo(1);
    }

    private long contarDesbloqueos() {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM renaser.desbloqueos_habito WHERE participante_id = ? AND habito_id = ?",
                Long.class, participanteId.value(), habitoId.value());
        return total == null ? 0 : total;
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
