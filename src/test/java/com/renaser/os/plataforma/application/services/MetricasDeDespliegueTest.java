package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.out.MetricasDeDesplieguePort;
import com.renaser.os.plataforma.domain.model.despliegue.ConteoDeDespliegue;
import com.renaser.os.plataforma.domain.model.despliegue.DespliegueDeFuncionalidad;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Las métricas del despliegue (contrato fase 2 §3.3): dentro/fuera, 5xx, lote por minuto y retención. Sin Spring. */
class MetricasDeDespliegueTest {

    private final RelojMovil reloj = new RelojMovil(Instant.parse("2026-10-14T03:59:58Z"));
    private final UserId soporte = UserId.of(UUID.randomUUID());
    private final UserId probadora = UserId.of(UUID.randomUUID());
    private final UserId otra = UserId.of(UUID.randomUUID());
    private final Dobles.Despliegues despliegues = new Dobles.Despliegues();
    private final CopiaDeDespliegues copia = new CopiaDeDespliegues(despliegues);
    private final Guardadas guardadas = new Guardadas();
    private final MetricasDeDespliegue metricas = new MetricasDeDespliegue(copia, guardadas, reloj, Duration.ofHours(48));

    @Test
    @DisplayName("sin despliegues no cuenta nada (el camino de casi todos los pedidos)")
    void sinDespliegues() {
        metricas.registrar("chat.grupos", otra, true);

        assertThat(metricas.escribirPendientes()).isZero();
        assertThat(guardadas.lotes).isEmpty();
    }

    @Test
    @DisplayName("separa dentro y fuera, cuenta los 5xx, y un pedido a una hija suma en la madre en despliegue")
    void dentroFueraYErrores() {
        enDespliegue("chat", 0);
        metricas.registrar("chat.grupos", probadora, false);
        metricas.registrar("chat.grupos", probadora, true);
        metricas.registrar("chat", otra, false);
        metricas.registrar("muro", otra, true);
        metricas.registrar("chat.grupos", null, true);

        assertThat(metricas.escribirPendientes()).isEqualTo(2);

        Instant hora = Instant.parse("2026-10-14T03:00:00Z");
        assertThat(guardadas.todas()).containsExactlyInAnyOrder(
                new ConteoDeDespliegue("chat", hora, true, 2, 1),
                new ConteoDeDespliegue("chat", hora, false, 1, 0));
    }

    @Test
    @DisplayName("lote por minuto: escribir vacía la memoria; lo que llega después va en el lote siguiente y en su hora")
    void lotePorMinuto() {
        enDespliegue("eventos", 0);
        metricas.registrar("eventos", probadora, false);
        metricas.escribirPendientes();
        reloj.avanzar(Duration.ofSeconds(5));
        metricas.registrar("eventos", probadora, false);

        assertThat(metricas.escribirPendientes()).isEqualTo(1);
        assertThat(metricas.escribirPendientes()).as("nada pendiente").isZero();
        assertThat(guardadas.lotes).hasSize(2);
        assertThat(guardadas.lotes.get(1)).singleElement()
                .extracting(ConteoDeDespliegue::hora).isEqualTo(Instant.parse("2026-10-14T04:00:00Z"));
    }

    @Test
    @DisplayName("si escribir falla, el lote vuelve a la memoria y sale entero en el intento siguiente")
    void reintento() {
        enDespliegue("eventos", 0);
        metricas.registrar("eventos", otra, false);
        guardadas.fallarUnaVez.set(true);

        assertThat(metricas.escribirPendientes()).isZero();
        metricas.registrar("eventos", otra, true);
        metricas.escribirPendientes();

        assertThat(guardadas.todas()).containsExactly(new ConteoDeDespliegue("eventos",
                Instant.parse("2026-10-14T03:00:00Z"), false, 2, 1));
    }

    @Test
    @DisplayName("contar y escribir a la vez no pierde ningún pedido (8 hilos × 5000)")
    void concurrencia() throws Exception {
        enDespliegue("eventos", 0);
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        CountDownLatch listos = new CountDownLatch(8);
        AtomicBoolean contando = new AtomicBoolean(true);
        for (int h = 0; h < 8; h++) {
            hilos.submit(() -> {
                for (int i = 0; i < 5000; i++) {
                    metricas.registrar("eventos", probadora, i % 10 == 0);
                }
                listos.countDown();
            });
        }
        Thread escritor = Thread.ofPlatform().start(() -> {
            while (contando.get()) {
                metricas.escribirPendientes();
            }
        });
        assertThat(listos.await(30, TimeUnit.SECONDS)).isTrue();
        contando.set(false);
        escritor.join();
        metricas.escribirPendientes();
        hilos.shutdown();

        assertThat(guardadas.todas().stream().mapToLong(ConteoDeDespliegue::pedidos).sum()).isEqualTo(40_000);
        assertThat(guardadas.todas().stream().mapToLong(ConteoDeDespliegue::errores).sum()).isEqualTo(4_000);
    }

    @Test
    @DisplayName("la purga borra lo de más de 48 h, contadas desde la hora actual")
    void purga() {
        metricas.purgarViejas();

        assertThat(guardadas.limiteDeLaPurga.get()).isEqualTo(Instant.parse("2026-10-12T03:00:00Z"));
    }

    private void enDespliegue(String clave, int porcentaje) {
        despliegues.empezar(DespliegueDeFuncionalidad.empezar(clave, porcentaje, 1, soporte, reloj.now())
                .conProbadora(probadora));
        copia.recargar();
    }

    /** La base de métricas en memoria; cada lote queda aparte para ver que se escribe en tandas. */
    private static final class Guardadas implements MetricasDeDesplieguePort {

        final List<List<ConteoDeDespliegue>> lotes = new ArrayList<>();
        final AtomicBoolean fallarUnaVez = new AtomicBoolean();
        final AtomicReference<Instant> limiteDeLaPurga = new AtomicReference<>();

        @Override
        public synchronized void sumar(Collection<ConteoDeDespliegue> conteos) {
            if (fallarUnaVez.getAndSet(false)) {
                throw new IllegalStateException("la base no responde");
            }
            lotes.add(List.copyOf(conteos));
        }

        @Override
        public List<ConteoDeDespliegue> desde(String clave, Instant hora) {
            return todas();
        }

        @Override
        public int borrarAnterioresA(Instant limite) {
            limiteDeLaPurga.set(limite);
            return 0;
        }

        /** Lo guardado, sumado por celda como lo hace el ON CONFLICT DO UPDATE. */
        synchronized List<ConteoDeDespliegue> todas() {
            Map<String, ConteoDeDespliegue> porCelda = new LinkedHashMap<>();
            lotes.stream().flatMap(List::stream).forEach(c -> porCelda.merge(c.clave() + c.hora() + c.dentro(), c,
                    (a, b) -> new ConteoDeDespliegue(a.clave(), a.hora(), a.dentro(), a.pedidos() + b.pedidos(),
                            a.errores() + b.errores())));
            return List.copyOf(porCelda.values());
        }
    }

    private static final class RelojMovil implements Clock {

        private Instant ahora;

        RelojMovil(Instant ahora) {
            this.ahora = ahora;
        }

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }

        @Override
        public Instant now() {
            return ahora;
        }

        @Override
        public LocalDate today() {
            return ahora.atZone(ZoneOffset.UTC).toLocalDate();
        }
    }
}
