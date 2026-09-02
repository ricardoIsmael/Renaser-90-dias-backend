package com.renaser.os.habits.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.espiritu.ConsultarEstadoEspirituUseCase;
import com.renaser.os.habits.application.ports.out.espiritu.AudioCatalogPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresion de C-10 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html):
 * {@code consultar()} es una lectura que, si hace falta, desbloquea el siguiente audio
 * escribiendo una fila nueva — dos lecturas concurrentes del mismo participante no deben
 * duplicar la fila ni terminar en un 409. Requiere Postgres real: la carrera contra el
 * {@code UNIQUE(participante_id, dia)} no se puede probar con mocks.
 *
 * <p>Deliberadamente SIN {@code @Transactional} de clase (mismo motivo que
 * {@code RocaDiariaConcurrenciaTest}): los hilos del {@code ExecutorService}, cada uno con su
 * propia conexion/transaccion, jamas verian la semilla del hilo principal si el test corriera
 * dentro de una unica transaccion sin commit.
 *
 * <p>El {@link AudioCatalogPort} real de produccion es un {@code NoOpAudioCatalogAdapter}
 * (Google Drive todavia no esta integrado — CLAUDE.MD §11) que siempre devuelve vacio, asi
 * que sin reemplazarlo el camino de escritura de {@code EspirituService} nunca se alcanzaria.
 * Se lo reemplaza por un catalogo fijo con el unico dia que este test necesita.
 *
 * <p>El reloj tambien va fijo, y no es un detalle: {@code asegurarAvance} retorna sin hacer
 * nada antes de {@code HORA_DESBLOQUEO} (07:00 en la zona del participante). Con el reloj del
 * sistema este test pasaba o fallaba segun la hora a la que se corriera — fallaba de
 * madrugada, que es cuando se corrio por primera vez. El reloj entra por el puerto
 * {@code Clock} justamente para esto (CLAUDE.MD §5).
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, EspirituConcurrenciaTest.CatalogoFijoConfig.class})
class EspirituConcurrenciaTest {

    private static final int INTENTOS_CONCURRENTES = 6;

    @Autowired
    private ConsultarEstadoEspirituUseCase consultarUseCase;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID participanteId;

    @BeforeEach
    void seedParticipante() {
        participanteId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (?, ?, 'Fixture', CAST('APRENDIZ' AS renaser.rol_usuario), CAST('ACTIVO' AS renaser.estado_usuario))
                        """,
                participanteId, participanteId + "@renaser.test");
        // diaPrograma 8 -> audioDay 1 (AUDIO_UNLOCK_START_DAY = 7, ver EspirituService).
        jdbcTemplate.update("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone)
                        VALUES (?, 8, 'UTC')
                        """,
                participanteId);
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra participantes_programa / registros_espiritu.
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", participanteId);
    }

    @Test
    @DisplayName("C-10: N lecturas concurrentes del mismo participante -> un solo registro de Espiritu, sin 409")
    void lecturasConcurrentesNoDuplicanNiFallan() throws InterruptedException {
        UserId actor = UserId.of(participanteId);
        ExecutorService pool = Executors.newFixedThreadPool(INTENTOS_CONCURRENTES);
        List<Future<Void>> resultados;
        try {
            List<Callable<Void>> intentos = IntStream.range(0, INTENTOS_CONCURRENTES)
                    .<Callable<Void>>mapToObj(i -> () -> {
                        consultarUseCase.consultar(actor);
                        return null;
                    })
                    .toList();
            resultados = pool.invokeAll(intentos, 30, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }

        // El bug (C-10): sin el arreglo, la mitad de estas llamadas terminaba en
        // DataIntegrityViolationException (409 via GlobalExceptionHandler) por chocar contra
        // el UNIQUE(participante_id, dia) — para una simple lectura.
        resultados.forEach(this::requireSinFallar);

        Long filas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM renaser.registros_espiritu WHERE participante_id = ? AND dia = 1", Long.class,
                participanteId);
        assertThat(filas).as("una sola fila desbloqueada pese a %s lecturas concurrentes", INTENTOS_CONCURRENTES)
                .isEqualTo(1);
    }

    private void requireSinFallar(Future<Void> future) {
        try {
            future.get();
        } catch (Exception e) {
            throw new RuntimeException("Fallo inesperado en una lectura concurrente (era justo lo que C-10 arreglaba)",
                    e);
        }
    }

    @TestConfiguration
    static class CatalogoFijoConfig {

        /** 10:00 UTC: pasada la HORA_DESBLOQUEO de las 07:00, con el dia todavia lejos del limite. */
        @Bean
        @Primary
        Clock relojFijo() {
            return FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
        }

        @Bean
        @Primary
        AudioCatalogPort audioCatalogPortFijo() {
            AudioCatalogPort.AudioEspiritu dia1 = new AudioCatalogPort.AudioEspiritu(1, "Dia 1", "drive-fixture",
                    "audio/mpeg", 1000);
            return new AudioCatalogPort() {
                @Override
                public Optional<AudioEspiritu> porDia(int dia) {
                    return dia == 1 ? Optional.of(dia1) : Optional.empty();
                }

                @Override
                public List<AudioEspiritu> todos() {
                    return List.of(dia1);
                }
            };
        }
    }
}
