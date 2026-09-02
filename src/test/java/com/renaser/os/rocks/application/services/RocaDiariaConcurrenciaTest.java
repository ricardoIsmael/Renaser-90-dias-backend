package com.renaser.os.rocks.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rocks.api.RocaCompletadaEvent;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CompletarRocaDiariaUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CompletarRocaDiariaUseCase.CompletarRocaDiariaCommand;
import com.renaser.os.rocks.application.ports.out.rocadiaria.SaveRocaDiariaPort;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId;
import com.renaser.os.rocks.domain.model.rocadiaria.TipoEvidenciaRoca;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
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
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresion de C-2 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html):
 * dos completados concurrentes de la MISMA Roca Diaria (doble toque en el celular,
 * reintento por timeout de red) no deben producir doble evidencia, doble premio ni
 * doble {@link RocaCompletadaEvent}. Requiere Postgres real: el bloqueo pesimista de
 * {@code SpringDataRocaDiariaRepository.findByIdParaEscritura} no se puede probar con
 * mocks (por eso NO es el {@code RocaDiariaServiceTest} de siempre).
 *
 * <p>Deliberadamente SIN {@code @Transactional} de clase: si el test corriera dentro
 * de una unica transaccion, los hilos de {@code ExecutorService} (cada uno con su
 * propia conexion/transaccion) jamas verian la roca sembrada en el hilo principal —
 * la transaccion del test nunca hace commit. El seed y la limpieza usan
 * {@code JdbcTemplate} (auto-commit por sentencia), igual que
 * {@code AjustePuntosPersistenceAdapterTest}.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, RocaDiariaConcurrenciaTest.CapturaEventosConfig.class})
class RocaDiariaConcurrenciaTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
    private static final int INTENTOS_CONCURRENTES = 6;

    @Autowired
    private CompletarRocaDiariaUseCase completarUseCase;
    @Autowired
    private SaveRocaDiariaPort saveRocaDiariaPort;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private CapturaEventos capturaEventos;

    private UserId participanteId;

    @BeforeEach
    void seedParticipante() {
        participanteId = UserId.of(UUID.randomUUID());
        jdbcTemplate.update("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (?, ?, 'Fixture', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """,
                participanteId.value(), participanteId + "@renaser.test");
        jdbcTemplate.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa) VALUES (?, 20)",
                participanteId.value());
        capturaEventos.eventos().clear();
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra participantes_programa / rocas_diarias / evidencias / ajustes_puntos_liga.
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", participanteId.value());
    }

    /** Sin horaFin: EscalaPuntosRoca.calcular siempre da A_TIEMPO (10 puntos), sin depender del reloj real. */
    private RocaDiariaId crearRocaVerdeSinCompletar() {
        RocaDiariaId id = RocaDiariaId.of(UUID.randomUUID());
        RocaDiaria roca = RocaDiaria.planificar(id, participanteId, LocalDate.of(2026, 8, 24), 1, "Meditar 10 min",
                null, 5, false, EjeObjetivo.CUERPO, null, null, null, CLOCK);
        saveRocaDiariaPort.save(roca);
        return id;
    }

    private CompletarRocaDiariaCommand comandoTexto(RocaDiariaId id) {
        return new CompletarRocaDiariaCommand(participanteId, id, TipoEvidenciaRoca.TEXTO, null, null,
                "Lo hice", null, null, null, true, false);
    }

    @Test
    @DisplayName("C-2: un completado normal (sin concurrencia) sigue funcionando igual que antes del arreglo")
    void completadoNormalSigueFuncionando() {
        RocaDiariaId id = crearRocaVerdeSinCompletar();

        RocaDiaria resultado = completarUseCase.completar(comandoTexto(id));

        assertThat(resultado.completada()).isTrue();
        assertThat(resultado.puntosOtorgados()).isEqualTo(10);
        assertThat(contarEvidencias(id)).isEqualTo(1);
        assertThat(contarAjustesPuntos()).isEqualTo(1);
        assertThat(capturaEventos.eventos()).hasSize(1);
    }

    @Test
    @DisplayName("C-2: dos completados concurrentes de la misma roca -> una sola evidencia, un solo premio, un solo evento")
    void dobleCompletadoConcurrenteNoDuplicaNada() throws InterruptedException {
        RocaDiariaId id = crearRocaVerdeSinCompletar();
        ExecutorService pool = Executors.newFixedThreadPool(INTENTOS_CONCURRENTES);
        List<Future<Boolean>> resultados;
        try {
            List<Callable<Boolean>> intentos = IntStream.range(0, INTENTOS_CONCURRENTES)
                    .<Callable<Boolean>>mapToObj(i -> () -> {
                        try {
                            completarUseCase.completar(comandoTexto(id));
                            return true; // gano la carrera
                        } catch (IllegalStateException ex) {
                            return false; // perdio la carrera: ALREADY_COMPLETED (409), comportamiento esperado
                        }
                    })
                    .toList();
            resultados = pool.invokeAll(intentos, 30, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }

        long exitosos = contarExitos(resultados);

        // El bug (C-2): sin el bloqueo pesimista, esto daba 6 exitosos, 6 evidencias,
        // 6 asientos de puntos y 6 eventos. Con el arreglo, exactamente 1 de cada uno.
        assertThat(exitosos).as("solo UNA de las %s llamadas concurrentes debe completar la roca",
                INTENTOS_CONCURRENTES).isEqualTo(1);
        assertThat(contarEvidencias(id)).as("una sola evidencia registrada, no una por intento").isEqualTo(1);
        assertThat(contarAjustesPuntos()).as("un solo asiento de puntos, no uno por intento").isEqualTo(1);
        assertThat(capturaEventos.eventos()).as("un solo RocaCompletadaEvent publicado").hasSize(1);

        Integer puntosOtorgados = jdbcTemplate.queryForObject(
                "SELECT puntos_otorgados FROM renaser.rocas_diarias WHERE id = ?", Integer.class, id.value());
        assertThat(puntosOtorgados).as("el premio no se duplica pese a los 6 intentos").isEqualTo(10);
    }

    private static long contarExitos(List<Future<Boolean>> resultados) {
        return resultados.stream().filter(RocaDiariaConcurrenciaTest::obtenerResultado).count();
    }

    private static boolean obtenerResultado(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException("Fallo inesperado (no IllegalStateException) en un intento concurrente", e);
        }
    }

    private long contarEvidencias(RocaDiariaId id) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM renaser.evidencias WHERE roca_diaria_id = ?", Long.class, id.value());
        return total == null ? 0 : total;
    }

    private long contarAjustesPuntos() {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM renaser.ajustes_puntos_liga WHERE participante_id = ?", Long.class,
                participanteId.value());
        return total == null ? 0 : total;
    }

    /** Captura in-memory de {@link RocaCompletadaEvent} para verificar que se publique una sola vez. */
    @TestConfiguration
    static class CapturaEventosConfig {
        @Bean
        CapturaEventos capturaEventos() {
            return new CapturaEventos();
        }
    }

    static class CapturaEventos {
        private final List<RocaCompletadaEvent> eventos = new CopyOnWriteArrayList<>();

        @EventListener
        void alRecibir(RocaCompletadaEvent evento) {
            eventos.add(evento);
        }

        List<RocaCompletadaEvent> eventos() {
            return eventos;
        }
    }
}
