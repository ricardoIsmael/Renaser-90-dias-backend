package com.renaser.os.onboarding.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.onboarding.application.ports.in.grabacionv90.ValidarV90UseCase;
import com.renaser.os.onboarding.application.ports.in.grabacionv90.ValidarV90UseCase.SolicitarValidacionV90Command;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.ValidacionIAPort;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.ValidacionIAPort.ResultadoValidacionV90;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Regresion de C-3 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html): dos
 * {@code POST .../validation} concurrentes sobre la MISMA grabacion V90 (doble clic,
 * reintento del cliente por timeout) no deben disparar dos llamadas a la IA ni perder el
 * contador de intentos. Requiere Postgres real: el bloqueo pesimista de
 * {@code SpringDataGrabacionV90Repository.findByIdParaEscritura} no se puede probar con mocks
 * (por eso NO es el {@code GrabacionV90ServiceTest} de siempre) — mismo criterio que
 * {@code RocaDiariaConcurrenciaTest} (C-2).
 *
 * <p>Deliberadamente SIN {@code @Transactional} de clase, por el mismo motivo que
 * {@code RocaDiariaConcurrenciaTest} y {@code ProcesarValidacionV90ServiceTransaccionIT}: los
 * hilos del {@code ExecutorService} (cada uno con su propia conexion) necesitan ver el seed ya
 * comprometido, y el trabajo real de validacion corre {@code @Async} en OTRO hilo despues del
 * commit — con una transaccion de test ambiente ninguna de las dos cosas pasaria.
 *
 * <p>El mock de {@link ValidacionIAPort} se bloquea con un {@link CountDownLatch} hasta que
 * las 6 solicitudes concurrentes ya terminaron: sin este control, el ciclo completo
 * "IA responde -> vuelve a PENDIENTE" del ganador podria resolverse ANTES de que los hilos
 * perdedores lleguen a leer, abriendo una segunda ventana de carrera distinta a la que este
 * test quiere aislar (arrancar dos validaciones sobre el MISMO intento en PROCESANDO).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GrabacionV90ValidacionConcurrenciaIT {

    private static final int INTENTOS_CONCURRENTES = 6;

    @Autowired
    private ValidarV90UseCase validarUseCase;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private ValidacionIAPort validacionIAPort;

    private UserId usuarioId;
    private long grabacionId;

    @BeforeEach
    void seedGrabacionPendienteDeValidar() {
        usuarioId = UserId.of(UUID.randomUUID());
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Fixture', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                """, usuarioId.value(), usuarioId + "@renaser.test");

        Long mediaId = jdbcTemplate.queryForObject("""
                INSERT INTO renaser.medias_onboarding (usuario_id, clase, bucket, ruta_storage)
                VALUES (?, 'audio', 'onboarding-media', ?) RETURNING id
                """, Long.class, usuarioId.value(), "onboarding/" + usuarioId + "/audio/" + UUID.randomUUID());

        grabacionId = jdbcTemplate.queryForObject("""
                INSERT INTO renaser.grabaciones_v90
                    (usuario_id, fase, eje, indice, clave_pregunta, grabada, media_id, transcripcion,
                     estado_ia, intentos_ia)
                VALUES (?, 'FASE_1', 'MENTE', 0, 'v90_mente_0', true, ?, 'transcripcion',
                        CAST('PENDIENTE' AS renaser.estado_ia_v90), 0)
                RETURNING id
                """, Long.class, usuarioId.value(), mediaId);
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra medias_onboarding / grabaciones_v90.
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", usuarioId.value());
    }

    @Test
    @DisplayName("C-3: dos POST /validation concurrentes sobre la misma grabacion -> una sola llamada a la IA, "
            + "un solo intento consumido, ninguna solicitud falla")
    void dobleSolicitudConcurrenteNoLlamaDosVecesALaIa() throws Exception {
        AtomicInteger llamadasIA = new AtomicInteger();
        CountDownLatch todasLasSolicitudesTerminaron = new CountDownLatch(1);
        when(validacionIAPort.validar(any())).thenAnswer(inv -> {
            llamadasIA.incrementAndGet();
            // Retiene el veredicto hasta que las 6 solicitudes concurrentes ya volvieron:
            // aisla la carrera de "doble arranque" (C-3) del ciclo normal de reintento.
            todasLasSolicitudesTerminaron.await(10, TimeUnit.SECONDS);
            return ResultadoValidacionV90.noDisponible();
        });

        ExecutorService pool = Executors.newFixedThreadPool(INTENTOS_CONCURRENTES);
        List<Future<Void>> resultados;
        try {
            List<Callable<Void>> intentos = IntStream.range(0, INTENTOS_CONCURRENTES)
                    .<Callable<Void>>mapToObj(i -> () -> {
                        validarUseCase.solicitarValidacion(new SolicitarValidacionV90Command(usuarioId, grabacionId));
                        return null;
                    })
                    .toList();
            resultados = pool.invokeAll(intentos, 30, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }
        todasLasSolicitudesTerminaron.countDown();

        // Contrato (C-3): NINGUNA de las 6 solicitudes concurrentes debe fallar. La ganadora
        // arranca la validacion; las demas responden el mismo 202 idempotente sin relanzar
        // una segunda validacion IA.
        for (Future<Void> resultado : resultados) {
            resultado.get();
        }

        esperarQueSalgaDeProcesando();

        assertThat(llamadasIA.get())
                .as("solo UNA de las %s solicitudes concurrentes debe llamar a la IA", INTENTOS_CONCURRENTES)
                .isEqualTo(1);

        Short intentosPersistidos = jdbcTemplate.queryForObject(
                "SELECT intentos_ia FROM renaser.grabaciones_v90 WHERE id = ?", Short.class, grabacionId);
        assertThat(intentosPersistidos).as("un solo intento consumido, no uno por solicitud concurrente")
                .isEqualTo((short) 1);

        String estadoFinal = jdbcTemplate.queryForObject(
                "SELECT estado_ia::text FROM renaser.grabaciones_v90 WHERE id = ?", String.class, grabacionId);
        // IA "no disponible" con 1 de 3 intentos consumidos -> reintentable (PENDIENTE), no REVISION_MANUAL.
        assertThat(estadoFinal).isEqualTo("PENDIENTE");
    }

    /** Poll corto (sin Awaitility, no esta en el classpath — mismo criterio que
     * {@code NotificationsEventOutboxTest}): el {@code @Async} del ganador corre en otro hilo
     * despues del commit, asi que la fila puede tardar un momento en salir de PROCESANDO. */
    private void esperarQueSalgaDeProcesando() throws InterruptedException {
        long limite = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < limite) {
            String estado = jdbcTemplate.queryForObject(
                    "SELECT estado_ia::text FROM renaser.grabaciones_v90 WHERE id = ?", String.class, grabacionId);
            if (!"PROCESANDO".equals(estado)) {
                return;
            }
            Thread.sleep(200);
        }
    }
}
