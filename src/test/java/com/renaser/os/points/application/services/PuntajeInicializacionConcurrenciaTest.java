package com.renaser.os.points.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.points.application.ports.in.puntaje.AjustarPuntosUseCase;
import com.renaser.os.points.application.ports.in.puntaje.AjustarPuntosUseCase.AjustarPuntosCommand;
import com.renaser.os.points.domain.model.puntaje.PuntajeParticipante;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresion de C-12 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html):
 * {@code SELECT ... FOR UPDATE} no bloquea una fila que todavia no existe, asi que dos
 * ajustes de puntos concurrentes sobre un participante RECIEN INSCRITO (ej. sus dos
 * primeros habitos completados casi a la vez el dia 1) llegaban los dos a
 * {@code PuntajeParticipante.inicial(...)} en memoria, y el segundo {@code save()}
 * (merge) violaba la PK de {@code puntajes_participante} -> 409 en el primer habito del
 * programa, con el ajuste perdido. Requiere Postgres real: la serializacion de
 * {@code INSERT ... ON CONFLICT DO NOTHING} contra un INSERT concurrente en la MISMA fila
 * inexistente no se puede probar con mocks — mismo motivo por el que
 * {@code RocaDiariaConcurrenciaTest} (C-2) y {@code EvidenciaAnularConcurrenciaTest} (C-13)
 * tampoco lo hacen.
 *
 * <p>Deliberadamente SIN {@code @Transactional} de clase: si el test corriera dentro de una
 * unica transaccion, los hilos del {@code ExecutorService} (cada uno con su propia
 * conexion/transaccion) jamas verian al participante sembrado en el hilo principal — la
 * transaccion del test nunca hace commit. El seed y la limpieza usan {@code JdbcTemplate}
 * (auto-commit por sentencia), igual que {@code RocaDiariaConcurrenciaTest}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PuntajeInicializacionConcurrenciaTest {

    private static final int INTENTOS_CONCURRENTES = 6;
    private static final int DELTA_POR_INTENTO = 10;

    @Autowired
    private AjustarPuntosUseCase ajustarUseCase;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserId participanteId;

    @BeforeEach
    void seedParticipanteSinFilaDePuntaje() {
        participanteId = UserId.of(UUID.randomUUID());
        jdbcTemplate.update("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (?, ?, 'Fixture', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """,
                participanteId.value(), participanteId + "@renaser.test");
        // dia_programa=1: exactamente el escenario de C-12, el primer dia del programa,
        // ANTES de que exista ninguna fila en puntajes_participante para este participante.
        jdbcTemplate.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa) VALUES (?, 1)",
                participanteId.value());
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra participantes_programa / puntajes_participante / ajustes_puntos_liga.
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", participanteId.value());
    }

    @Test
    @DisplayName("C-12: un ajuste normal (sin concurrencia) sobre un participante sin fila previa "
            + "sigue arrancando en 100 y aplicando el delta")
    void ajusteNormalSobreParticipanteSinFilaSigueFuncionando() {
        ajustarUseCase.ajustar(comando());

        assertThat(puntosLigaActuales()).isEqualTo(PuntajeParticipante.PUNTOS_LIGA_INICIAL + DELTA_POR_INTENTO);
        assertThat(contarFilasPuntaje()).isEqualTo(1L);
        assertThat(contarAjustesPuntos()).isEqualTo(1L);
    }

    @Test
    @DisplayName("C-12: seis ajustes concurrentes sobre un participante SIN fila previa de puntaje "
            + "-> ninguno falla, y los seis quedan sumados (ninguno se pierde)")
    void ajustesConcurrentesSobreParticipanteSinFilaNoPierdenNingunPunto() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(INTENTOS_CONCURRENTES);
        List<Future<Integer>> resultados;
        try {
            List<Callable<Integer>> intentos = IntStream.range(0, INTENTOS_CONCURRENTES)
                    .<Callable<Integer>>mapToObj(i -> () -> ajustarUseCase.ajustar(comando()).deltaAplicado())
                    .toList();
            resultados = pool.invokeAll(intentos, 30, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }

        // El bug (C-12): sin INSERT ... ON CONFLICT DO NOTHING antes de la relectura con
        // PESSIMISTIC_WRITE, esto lanzaba una violacion de la PK de puntajes_participante en
        // cuanto dos hilos intentaban inicializar la fila a la vez -> 409 en el primer habito.
        // Con el arreglo, las 6 llamadas concurrentes deben completar sin lanzar.
        for (Future<Integer> resultado : resultados) {
            assertThat(obtenerResultado(resultado)).isEqualTo(DELTA_POR_INTENTO);
        }

        // Exactamente UNA fila de puntaje para el participante (no una carrera de INSERTs
        // duplicados) y el saldo final es la suma de los 6 ajustes sobre el saldo inicial:
        // ningun punto se pierde por la carrera de creacion de la fila.
        assertThat(contarFilasPuntaje()).as("una sola fila de puntaje, no una por intento").isEqualTo(1L);
        assertThat(puntosLigaActuales())
                .as("saldo inicial + los %s ajustes concurrentes, ninguno perdido", INTENTOS_CONCURRENTES)
                .isEqualTo(PuntajeParticipante.PUNTOS_LIGA_INICIAL + INTENTOS_CONCURRENTES * DELTA_POR_INTENTO);
        assertThat(contarAjustesPuntos())
                .as("un asiento en el ledger por cada ajuste, ninguno se pisa")
                .isEqualTo((long) INTENTOS_CONCURRENTES);
    }

    private AjustarPuntosCommand comando() {
        return new AjustarPuntosCommand(participanteId, MotivoPuntos.HABIT_COMPLETED, DELTA_POR_INTENTO,
                "C-12 concurrencia");
    }

    private static Integer obtenerResultado(Future<Integer> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException("Fallo inesperado (ningun intento deberia lanzar) en un ajuste concurrente",
                    e);
        }
    }

    private int puntosLigaActuales() {
        Integer puntos = jdbcTemplate.queryForObject(
                "SELECT puntos_liga FROM renaser.puntajes_participante WHERE participante_id = ?", Integer.class,
                participanteId.value());
        return puntos == null ? 0 : puntos;
    }

    private long contarFilasPuntaje() {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM renaser.puntajes_participante WHERE participante_id = ?", Long.class,
                participanteId.value());
        return total == null ? 0 : total;
    }

    private long contarAjustesPuntos() {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM renaser.ajustes_puntos_liga WHERE participante_id = ?", Long.class,
                participanteId.value());
        return total == null ? 0 : total;
    }
}
