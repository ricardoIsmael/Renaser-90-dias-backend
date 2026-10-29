package com.renaser.os.chat.infrastructure.adapter.in.websocket;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.chat.infrastructure.adapter.in.websocket.ConexionesStompDePrueba.Escucha;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static com.renaser.os.chat.infrastructure.adapter.in.websocket.ConexionesStompDePrueba.LATIDO;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * El cierre de la conexión muda (D-202, propuesto en E-331) contra el Tomcat real, con los MISMOS
 * latidos de producción pero de 2 s en vez de 10 s (E-648).
 *
 * <p><b>Por qué 2 s.</b> Con 10 s esta prueba esperaba 30–40 s de reloj real: el broker da por
 * muerta a una conexión tras 3 veces el intervalo sin leer nada de ella y la revisa una vez por
 * intervalo. Con 2 s son 6–8 s, y lo que se prueba es lo mismo: el mecanismo (el factor 3, el
 * programador del broker, los interceptores de salida, el cierre). El VALOR de 10 s lo fija
 * {@link LatidosDelChatIT}. Un contexto aparte porque el broker lee su intervalo al arrancar.
 *
 * <p><b>Por qué no se exige el {@code ERROR} en una conexión sola.</b> El 28/10 esta prueba frenó
 * dos veces el pipeline porque a veces el {@code ERROR Session closed.} no llega aunque el socket sí
 * se cierre con 1002 (E-648). No es una carrera del cliente (no aparece esperando más): es una
 * carrera del servidor. En la misma pasada de {@code SimpleBrokerMessageHandler.HeartbeatTask} el
 * broker declara muerta a la sesión (encola el {@code ERROR}) y, como no actualiza su última
 * escritura al latir, le encola también un latido; los dos salen por hilos distintos del canal de
 * salida y, si el latido gana, el cierre con 1002 que sigue al {@code ERROR} descarta el {@code
 * ERROR} que estaba en cola. Medido con 300 mudas a la vez: ~16 % cerradas con 1002 y sin {@code
 * ERROR} (tramas: CONNECTED y tres latidos). El 1002 sí es determinista —solo lo manda el servidor,
 * y solo cuando procesó ese {@code ERROR}— y la app reacciona igual a un {@code ERROR} o a un cierre
 * (`conexionStomp.ts`: el {@code ERROR} solo cierra, y {@code onclose} reconecta).
 *
 * <p>Para no debilitar la comprobación se prueban varias mudas a la vez: todas deben cerrarse con
 * 1002, ninguna puede recibir otra trama que no sea el saludo, latidos y un {@code ERROR Session
 * closed.}, y <b>al menos una</b> debe recibir el {@code ERROR}. Un servidor que nunca lo mande
 * falla siempre; con {@value #MUDAS} mudas y ~16 % de pérdida, que se pierdan todas
 * (0,16<sup>8</sup>) es del orden de 1 en 2 millones, o menos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LatidosDelChatCortosIT {

    private static final long LATIDO_CORTO_MS = 2_000;
    private static final String LATIDOS_CORTOS = LATIDO_CORTO_MS + "," + LATIDO_CORTO_MS;
    private static final int MUDAS = 8;

    /**
     * Cambia solo el intervalo del broker, antes de que arranque; el resto de {@code WebSocketConfig}
     * (programador, interceptores, factor 3 de {@code SimpleBrokerMessageHandler}) queda como en
     * producción. Nested {@code @TestConfiguration}: Spring Boot la toma sola para esta clase, y
     * por eso este contexto no se comparte con las demás pruebas. Que de verdad quedó en 2 s lo
     * comprueba la prueba al leer el {@code CONNECTED}.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class LatidosCortos {
        @Bean
        static BeanPostProcessor latidosCortosDelBroker() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessBeforeInitialization(Object bean, String beanName) {
                    if (bean instanceof SimpleBrokerMessageHandler broker) {
                        broker.setHeartbeatValue(new long[] {LATIDO_CORTO_MS, LATIDO_CORTO_MS});
                    }
                    return bean;
                }
            };
        }
    }

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;

    private ConexionesStompDePrueba conexiones;
    private final ScheduledExecutorService latidosDelCliente = Executors.newSingleThreadScheduledExecutor();

    @BeforeEach
    void abrir() {
        conexiones = new ConexionesStompDePrueba(puerto, sesiones);
    }

    @AfterEach
    void cerrar() {
        latidosDelCliente.shutdownNow();
        conexiones.close();
    }

    @Test
    @DisplayName("las conexiones mudas se cierran; la que late sigue abierta y recibe los latidos del servidor; "
            + "la que no ofreció latidos no se corta")
    void cierraSoloLasConexionesMudas() throws Exception {
        Escucha queLate = new Escucha();
        Escucha sinLatidos = new Escucha();
        List<Escucha> mudas = IntStream.range(0, MUDAS).mapToObj(i -> new Escucha()).toList();
        WebSocket socketQueLate = conexiones.conectar(queLate, LATIDOS_CORTOS);
        for (Escucha muda : mudas) {
            conexiones.conectar(muda, LATIDOS_CORTOS);
        }
        conexiones.conectar(sinLatidos, "0,0");
        queLate.esperarTrama("CONNECTED", 10);
        for (Escucha muda : mudas) {
            muda.esperarTrama("CONNECTED", 10);
        }
        sinLatidos.esperarTrama("CONNECTED", 10);
        assertThat(ConexionesStompDePrueba.cabeceras(queLate.esperarTrama("CONNECTED", 1)))
                .as("el contexto de esta prueba negocia los latidos cortos")
                .containsEntry("heart-beat", LATIDOS_CORTOS);
        // Como la app: un latido cada intervalo; acá cada medio para no depender del borde exacto de 3 × 2 s.
        latidosDelCliente.scheduleAtFixedRate(() -> ConexionesStompDePrueba.enviar(socketQueLate, LATIDO),
                LATIDO_CORTO_MS / 2, LATIDO_CORTO_MS / 2, TimeUnit.MILLISECONDS);

        // El broker da por muerta a una muda tras 3 × 2 s sin leer nada de ella, y la revisa cada 2 s.
        for (Escucha muda : mudas) {
            assertThat(muda.cierre.get(30, TimeUnit.SECONDS)).as("código de cierre de una muda").isEqualTo(1002);
        }
        // Solo el saludo, latidos y, a veces, el ERROR del cierre (ver la carrera de la clase).
        for (Escucha muda : mudas) {
            assertThat(muda.tramas).as("tramas de una muda").allSatisfy(trama -> assertThat(trama).satisfiesAnyOf(
                    t -> assertThat(t).startsWith("CONNECTED\n"),
                    t -> assertThat(t).isEqualTo(LATIDO),
                    t -> assertThat(t).startsWith("ERROR\n").contains("Session closed.")));
        }
        assertThat(mudas).as("al menos una muda recibió el ERROR «Session closed.» antes del cierre")
                .anySatisfy(muda -> assertThat(muda.tramasDeError()).isNotEmpty());

        assertThat(queLate.cierre).as("la que late sigue abierta").isNotDone();
        assertThat(queLate.latidosRecibidos()).as("latidos del servidor").isPositive();
        assertThat(sinLatidos.cierre).as("sin latidos acordados no se corta").isNotDone();
    }
}
