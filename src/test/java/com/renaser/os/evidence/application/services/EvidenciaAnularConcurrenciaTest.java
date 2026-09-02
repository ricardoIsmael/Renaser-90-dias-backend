package com.renaser.os.evidence.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.evidence.api.DestinoEvidencia;
import com.renaser.os.evidence.api.EstadoValidacion;
import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.evidence.application.ports.in.evidencia.AnularVeredictoUseCase;
import com.renaser.os.evidence.application.ports.in.evidencia.AnularVeredictoUseCase.AnularVeredictoCommand;
import com.renaser.os.evidence.application.ports.out.evidencia.LoadEvidenciaPort;
import com.renaser.os.evidence.application.ports.out.evidencia.SaveEvidenciaPort;
import com.renaser.os.evidence.domain.model.evidencia.Evidencia;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
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
 * Regresión de C-13 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html): dos
 * admins (o un doble clic) anulando la MISMA evidencia al mismo tiempo no deben revertir
 * la penalización de puntos dos veces. Requiere Postgres real: el bloqueo pesimista de
 * {@code SpringDataEvidenciaRepository.findByIdParaEscritura} no se puede probar con
 * mocks — mismo motivo por el que {@code RocaDiariaConcurrenciaTest} (C-2) tampoco lo hace.
 *
 * <p>Deliberadamente SIN {@code @Transactional} de clase: si el test corriera dentro de
 * una única transacción, los hilos del {@code ExecutorService} (cada uno con su propia
 * conexión/transacción) jamás verían la evidencia sembrada en el hilo principal — la
 * transacción del test nunca hace commit. El seed y la limpieza usan {@code JdbcTemplate}
 * (auto-commit por sentencia), igual que {@code RocaDiariaConcurrenciaTest}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EvidenciaAnularConcurrenciaTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-25T10:00:00Z"));
    private static final int INTENTOS_CONCURRENTES = 6;

    @Autowired
    private AnularVeredictoUseCase anularUseCase;
    @Autowired
    private SaveEvidenciaPort saveEvidenciaPort;
    @Autowired
    private LoadEvidenciaPort loadEvidenciaPort;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserId participanteId;
    private UserId adminId;
    private UUID rocaDiariaId;

    @BeforeEach
    void seedParticipanteAdminYRoca() {
        participanteId = UserId.of(UUID.randomUUID());
        adminId = UserId.of(UUID.randomUUID());
        jdbcTemplate.update("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (?, ?, 'Fixture Aprendiz', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """,
                participanteId.value(), participanteId + "@renaser.test");
        jdbcTemplate.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa) VALUES (?, 20)",
                participanteId.value());
        jdbcTemplate.update("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (?, ?, 'Fixture Admin', CAST('ADMIN' AS renaser.rol_usuario), 'ACTIVO')
                        """,
                adminId.value(), adminId + "@renaser.test");
        rocaDiariaId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO renaser.rocas_diarias
                            (id, participante_id, fecha, posicion, titulo, color, puntaje_impacto, eje)
                        VALUES (?, ?, CURRENT_DATE, 1, 'titulo', CAST('VERDE' AS renaser.color_pareto), 5,
                                CAST('CUERPO' AS renaser.eje_objetivo))
                        """,
                rocaDiariaId, participanteId.value());
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra participantes_programa / rocas_diarias / evidencias / ajustes_puntos_liga.
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", participanteId.value());
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", adminId.value());
    }

    /** Evidencia ya RECHAZADA con una penalización aplicada — el estado en el que la deja
     * el flujo normal (rechazo por IA o revisión manual) antes de que un admin la anule. */
    private EvidenciaId crearEvidenciaRechazadaConPenalizacion() {
        Evidencia evidencia = Evidencia.rehydrate(EvidenciaId.of(UUID.randomUUID()), participanteId,
                new DestinoEvidencia.RocaDiaria(rocaDiariaId), TipoEvidencia.TEXTO, null, null, "contenido", null,
                CLOCK.now(), null, null, false, EstadoValidacion.RECHAZADA, "rechazada por IA", 1, true, false,
                CLOCK.now());
        return saveEvidenciaPort.save(evidencia).id();
    }

    private AnularVeredictoCommand comando(EvidenciaId id) {
        return new AnularVeredictoCommand(adminId, id, "anulacion concurrente");
    }

    @Test
    @DisplayName("C-13: una anulacion normal (sin concurrencia) sigue revirtiendo la penalizacion una vez")
    void anulacionNormalSigueFuncionando() {
        EvidenciaId id = crearEvidenciaRechazadaConPenalizacion();

        Evidencia resultado = anularUseCase.anular(comando(id));

        assertThat(resultado.estadoValidacion()).isEqualTo(EstadoValidacion.ANULADA_ADMIN);
        assertThat(resultado.penalizacionAplicada()).isFalse();
        assertThat(contarAjustesPuntos()).isEqualTo(1);
    }

    @Test
    @DisplayName("C-13: seis anulaciones concurrentes de la misma evidencia -> la penalizacion se revierte "
            + "UNA sola vez")
    void anulacionesConcurrentesNoRevierteLaPenalizacionDosVeces() throws InterruptedException {
        EvidenciaId id = crearEvidenciaRechazadaConPenalizacion();
        ExecutorService pool = Executors.newFixedThreadPool(INTENTOS_CONCURRENTES);
        List<Future<Evidencia>> resultados;
        try {
            List<Callable<Evidencia>> intentos = IntStream.range(0, INTENTOS_CONCURRENTES)
                    .<Callable<Evidencia>>mapToObj(i -> () -> anularUseCase.anular(comando(id)))
                    .toList();
            resultados = pool.invokeAll(intentos, 30, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }

        // anular() es idempotente por diseño: las 6 llamadas deben devolver sin lanzar, y
        // las 6 deben ver la evidencia ya ANULADA_ADMIN (una la anula, las otras 5 llegan
        // despues del commit y encuentran el estado ya resuelto).
        for (Future<Evidencia> resultado : resultados) {
            assertThat(obtenerResultado(resultado).estadoValidacion()).isEqualTo(EstadoValidacion.ANULADA_ADMIN);
        }

        // El bug (C-13): sin el bloqueo pesimista, varios hilos leian penalizacionAplicada=true
        // a la vez (check-then-act) y cada uno le pedia a `points` que revirtiera la
        // penalizacion -> hasta 6 ajustes. Con el arreglo, exactamente 1.
        assertThat(contarAjustesPuntos()).as("un solo ajuste de puntos, no uno por intento concurrente").isEqualTo(1);

        Evidencia recargada = loadEvidenciaPort.byId(id).orElseThrow();
        assertThat(recargada.penalizacionAplicada()).isFalse();
    }

    private static Evidencia obtenerResultado(Future<Evidencia> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException("Fallo inesperado (no deberia lanzar: anular es idempotente) en un intento "
                    + "concurrente", e);
        }
    }

    private long contarAjustesPuntos() {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM renaser.ajustes_puntos_liga WHERE participante_id = ?", Long.class,
                participanteId.value());
        return total == null ? 0 : total;
    }
}
