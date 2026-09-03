package com.renaser.os.evidence.infrastructure.adapter.in.scheduler;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.evidence.application.ports.in.evidencia.ProcesarColaValidacionUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresión de C-5 (docs/informes/auditoria-fixes/C-5.md): dos "instancias" corriendo el
 * mismo barrido {@code evidence-procesar-cola-validacion} al mismo tiempo deben producir
 * UNA sola ejecución efectiva del caso de uso, no dos.
 *
 * <p><b>Por qué se simula con dos hilos y no con dos procesos:</b> ShedLock arbitra por fila
 * en la tabla {@code renaser.shedlock} sobre Postgres real (no se puede probar con mocks —
 * mismo motivo que {@code RocaDiariaConcurrenciaTest} necesita Testcontainers), y desde el
 * punto de vista del {@link net.javacrumbs.shedlock.core.LockProvider} dos hilos que compiten
 * por la misma fila son indistinguibles de dos instancias del proceso compitiendo por ella:
 * el lock vive en la base, no en memoria de un JVM. Se llama directamente al bean
 * {@link ProcesarColaValidacionScheduler#ejecutar()} (a través del proxy de Spring, así el
 * aspecto de {@code @SchedulerLock} se aplica igual que si lo disparara el cron) en vez de
 * esperar al {@code @Scheduled} real, para no depender de temporizadores.
 *
 * <p>El caso de uso real ({@code EvidenciaService.procesarLote}) se reemplaza por un doble
 * que cuenta invocaciones y duerme un momento — simula el trabajo que mantiene el lock
 * ocupado el tiempo suficiente para que el segundo hilo llegue a intentar tomarlo mientras
 * el primero todavía no lo soltó. No hace falta sembrar evidencias reales: lo que se prueba
 * es el mecanismo de lock, no la lógica de negocio de {@code evidence} (esa ya la cubren
 * {@code EvidenciaServiceTest} y los tests de {@code EvidenciaPersistenceAdapter}).
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class,
        ProcesarColaValidacionSchedulerLockTest.CasoDeUsoFalsoConfig.class})
class ProcesarColaValidacionSchedulerLockTest {

    @Autowired
    private ProcesarColaValidacionScheduler scheduler;
    @Autowired
    private CasoDeUsoFalso casoDeUsoFalso;

    @Test
    @DisplayName("C-5: dos ejecuciones concurrentes del barrido bloqueado producen una sola ejecución efectiva")
    void dosEjecucionesConcurrentesProducenUnaSolaEjecucionEfectiva() throws InterruptedException {
        casoDeUsoFalso.invocaciones().set(0);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch arranquen = new CountDownLatch(1);
        try {
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    listos.countDown();
                    awaitSilenciosamente(arranquen);
                    scheduler.ejecutar(); // pasa por el proxy: el aspecto @SchedulerLock intercepta acá
                });
            }
            // Ambos hilos arrancan lo mas simultaneos posible, para maximizar la chance de
            // que el segundo intente el lock mientras el primero todavia lo tiene.
            listos.await(10, TimeUnit.SECONDS);
            arranquen.countDown();
            pool.shutdown();
            boolean terminoATiempo = pool.awaitTermination(15, TimeUnit.SECONDS);
            assertThat(terminoATiempo).as("las dos ejecuciones deben terminar sin colgarse").isTrue();
        } finally {
            pool.shutdownNow();
        }

        // El bug (C-5): sin @SchedulerLock, esto daba 2 invocaciones del caso de uso real
        // -- dos instancias procesando el mismo lote "PENDIENTE" a la vez. Con el lock,
        // la segunda ni siquiera entra al cuerpo del metodo: ShedLock la descarta en
        // silencio (no lanza), asi que el hilo perdedor tampoco falla.
        assertThat(casoDeUsoFalso.invocaciones().get())
                .as("solo UNA de las dos ejecuciones concurrentes debe correr el caso de uso real")
                .isEqualTo(1);
    }

    private static void awaitSilenciosamente(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    /** Doble del caso de uso real: cuenta invocaciones y duerme lo suficiente para que el
     * segundo hilo alcance a competir por el lock mientras el primero todavia lo sostiene. */
    static class CasoDeUsoFalso implements ProcesarColaValidacionUseCase {
        private final AtomicInteger invocaciones = new AtomicInteger(0);

        @Override
        public int procesarLote() {
            invocaciones.incrementAndGet();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return 0;
        }

        AtomicInteger invocaciones() {
            return invocaciones;
        }
    }

    @TestConfiguration
    static class CasoDeUsoFalsoConfig {
        @Bean
        @Primary
        CasoDeUsoFalso casoDeUsoFalso() {
            return new CasoDeUsoFalso();
        }
    }
}
