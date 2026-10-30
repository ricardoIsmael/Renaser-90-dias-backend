package com.renaser.os.rag.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rag.application.ports.out.conversacion.LoadConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveMensajeRenasiaPort;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasiaId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-655 (issue #34): el scheduler publico 132 avisos para 81 personas en el mismo minuto; dos avisos de la misma
 * persona leian "sin conversacion", los dos la creaban y el segundo reventaba con
 * {@code conversaciones_renasia_pkey}: se perdia el mensaje de chat de ese aviso (corre en un oyente, no hay 5xx).
 *
 * <p>Contra Postgres real y con transacciones de verdad, porque cada oyente de Spring Modulith es UNA transaccion
 * ({@code @ApplicationModuleListener}) y la conversacion recien creada no se ve hasta que confirma. En la primera
 * prueba el segundo aviso escribe mientras el primero todavia no confirmo (el caso peor, sin azar); en la segunda,
 * una rafaga de avisos de la misma persona arrancan juntos.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MensajeProactivoConcurrenteIT {

    private static final int ESPERA_SEGUNDOS = 15;

    @Autowired
    private LoadMensajeRenasiaPort loadMensajePort;
    @Autowired
    private SaveMensajeRenasiaPort saveMensajePort;
    @Autowired
    private LoadConversacionRenasiaPort loadConversacionPort;
    @Autowired
    private SaveConversacionRenasiaPort saveConversacionPort;
    @Autowired
    private Clock clock;
    @Autowired
    private TransactionTemplate transacciones;
    @Autowired
    private JdbcTemplate jdbc;

    private final UUID usuario = UUID.randomUUID();

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.mensajes_renasia WHERE usuario_id = ?", usuario);
        jdbc.update("DELETE FROM renaser.conversaciones_renasia WHERE usuario_id = ?", usuario);
        jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", usuario);
    }

    private MensajeProactivoDelAcompanante proactivo() {
        return new MensajeProactivoDelAcompanante(loadMensajePort, saveMensajePort, loadConversacionPort,
                saveConversacionPort, clock);
    }

    private void crearAprendiz() {
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Aprendiz Concurrente', 'APRENDIZ', 'ACTIVO')
                """, usuario, usuario + "@renaser.test");
    }

    @Test
    @DisplayName("el segundo aviso escribe mientras el primero no confirmo: queda una conversacion y los dos mensajes")
    void dosAvisosSolapadosDejanUnaConversacionYLosDosMensajes() throws InterruptedException {
        crearAprendiz();
        MensajeProactivoDelAcompanante proactivo = proactivo();
        CountDownLatch primeroEscribio = new CountDownLatch(1);
        CountDownLatch soltarAlPrimero = new CountDownLatch(1);
        List<Throwable> fallos = new CopyOnWriteArrayList<>();

        Thread primero = new Thread(() -> correr(fallos, () -> transacciones.executeWithoutResult(estado -> {
            proactivo.escribir(idDelMensaje(), UserId.of(usuario), "primer aviso");
            primeroEscribio.countDown();
            esperar(soltarAlPrimero);
        })));
        Thread segundo = new Thread(() -> correr(fallos, () -> transacciones.executeWithoutResult(estado ->
                proactivo.escribir(idDelMensaje(), UserId.of(usuario), "segundo aviso"))));

        primero.start();
        assertThat(primeroEscribio.await(ESPERA_SEGUNDOS, TimeUnit.SECONDS)).isTrue();
        segundo.start();
        Thread.sleep(500); // el segundo llega a crear la conversacion y queda esperando al primero
        soltarAlPrimero.countDown();
        primero.join(ESPERA_SEGUNDOS * 1000L);
        segundo.join(ESPERA_SEGUNDOS * 1000L);

        assertThat(fallos).as("ningun aviso falla (antes: conversaciones_renasia_pkey)").isEmpty();
        assertThat(conversaciones()).isEqualTo(1);
        assertThat(contenidos()).containsExactlyInAnyOrder("primer aviso", "segundo aviso");
    }

    @Test
    @DisplayName("una rafaga de avisos de la misma persona que arrancan juntos: una conversacion y todos los mensajes")
    void unaRafagaDeAvisosDejaUnaConversacionYTodosLosMensajes() throws InterruptedException {
        crearAprendiz();
        MensajeProactivoDelAcompanante proactivo = proactivo();
        int avisos = 6;
        CountDownLatch largada = new CountDownLatch(1);
        List<Throwable> fallos = new CopyOnWriteArrayList<>();
        List<Thread> hilos = new ArrayList<>();
        for (int i = 0; i < avisos; i++) {
            String texto = "aviso " + i;
            Thread hilo = new Thread(() -> correr(fallos, () -> {
                esperar(largada);
                transacciones.executeWithoutResult(estado ->
                        proactivo.escribir(idDelMensaje(), UserId.of(usuario), texto));
            }));
            hilos.add(hilo);
            hilo.start();
        }
        largada.countDown();
        for (Thread hilo : hilos) {
            hilo.join(ESPERA_SEGUNDOS * 1000L);
        }

        assertThat(fallos).as("ningun aviso falla").isEmpty();
        assertThat(conversaciones()).isEqualTo(1);
        assertThat(contenidos()).hasSize(avisos);
    }

    private int conversaciones() {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.conversaciones_renasia WHERE usuario_id = ?",
                Integer.class, usuario);
    }

    private List<String> contenidos() {
        return jdbc.queryForList("SELECT contenido FROM renaser.mensajes_renasia WHERE usuario_id = ?",
                String.class, usuario);
    }

    private static MensajeRenasiaId idDelMensaje() {
        return MensajeRenasiaId.of(UUID.randomUUID());
    }

    private static void correr(List<Throwable> fallos, Runnable trabajo) {
        try {
            trabajo.run();
        } catch (Throwable e) {
            fallos.add(e);
        }
    }

    private static void esperar(CountDownLatch latch) {
        try {
            latch.await(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
