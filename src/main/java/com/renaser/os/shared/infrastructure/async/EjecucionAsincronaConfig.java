package com.renaser.os.shared.infrastructure.async;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.task.SimpleAsyncTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * Con que hilos corre cada {@code @Async} de la aplicacion (E-360, D-213).
 *
 * <p><b>Por que existe.</b> Todos los {@code @ApplicationModuleListener} (36) son {@code @Async}. Se creia
 * que corrian en el ejecutor de Spring Boot, acotado por {@code spring.task.execution.simple.concurrency-limit}
 * ({@code ASYNC_IA_CONCURRENCY_LIMIT}, C-1). No era asi: Spring Boot solo arma ese ejecutor si no hay
 * ningun otro {@code Executor} en el contexto ({@code TaskExecutorConfigurations.OnExecutorCondition}), y el
 * broker STOMP del chat declara cuatro ({@code clientInboundChannelExecutor}, {@code brokerChannelExecutor},
 * ...). Sin ejecutor por defecto, {@code @Async} caia en {@code new SimpleAsyncTaskExecutor()}: un hilo de
 * plataforma nuevo por cada aviso, sin ningun tope. En el log se ve en el nombre de los hilos
 * ({@code SimpleAsyncTaskExecutor-431}, recortado a {@code askExecutor-431}), no en {@code task-N}.
 *
 * <p>El 2026-09-27 el despacho de 29 recordatorios abrio 29 hilos a la vez; cada uno pedia dos conexiones
 * (la de su transaccion y la transaccion propia del INSERT de la notificacion) y el pool de 20 se agoto:
 * 17 avisos perdidos y la API esperando. Con mil personas el mismo minuto serian mil hilos.
 *
 * <p><b>Dos ejecutores, porque son dos trabajos distintos:</b>
 * <ul>
 *   <li>{@link #EJECUTOR_DE_EVENTOS} (el de por defecto, el de todos los listeners): pocos hilos fijos y una
 *       cola. Su trabajo es base de datos, y cada hilo puede tener dos conexiones a la vez, asi que
 *       {@code 2 x renaser.eventos.concurrencia} tiene que dejar margen dentro de {@code DB_POOL_MAX_SIZE}
 *       para la API (4 hilos = 8 de 20 conexiones). Quien publica no espera: la cola absorbe la rafaga y
 *       los avisos salen de a {@code concurrencia} por vez. Lo que se pierda de la cola en memoria (un
 *       apagado) sigue en el outbox ({@code event_publication}) y se reintenta.</li>
 *   <li>{@link #EJECUTOR_DE_IA}: la validacion V90, que espera a la IA hasta un minuto sin tener ninguna
 *       conexion tomada. Es el ejecutor que C-1 describio y que nunca existio: hilos virtuales con el tope
 *       de {@code ASYNC_IA_CONCURRENCY_LIMIT}. Va aparte para que una tanda de validaciones no deje a los
 *       avisos esperando detras de la IA.</li>
 * </ul>
 */
@Configuration
public class EjecucionAsincronaConfig implements AsyncConfigurer {

    /** El ejecutor por defecto de {@code @Async}: el de los listeners de eventos entre modulos. */
    public static final String EJECUTOR_DE_EVENTOS = "ejecutorDeEventos";
    /** El de las tareas {@code @Async} que esperan a la IA sin tocar la base (validacion V90). */
    public static final String EJECUTOR_DE_IA = "ejecutorDeIa";

    /** Cuanto espera el apagado a que terminen los avisos en curso antes de cortar. */
    private static final Duration ESPERA_AL_APAGAR = Duration.ofSeconds(30);

    private final int concurrenciaDeEventos;

    EjecucionAsincronaConfig(@Value("${renaser.eventos.concurrencia:4}") int concurrenciaDeEventos) {
        if (concurrenciaDeEventos < 1) {
            throw new IllegalArgumentException("renaser.eventos.concurrencia debe ser al menos 1: "
                    + concurrenciaDeEventos);
        }
        this.concurrenciaDeEventos = concurrenciaDeEventos;
    }

    /**
     * Hilos fijos y cola sin tope practico: publicar un evento nunca bloquea a quien publica (un barrido,
     * el hilo de una request) y la rafaga se procesa a su ritmo.
     */
    @Bean(name = EJECUTOR_DE_EVENTOS)
    ThreadPoolTaskExecutor ejecutorDeEventos() {
        ThreadPoolTaskExecutor ejecutor = new ThreadPoolTaskExecutor();
        ejecutor.setThreadNamePrefix("eventos-");
        ejecutor.setCorePoolSize(concurrenciaDeEventos);
        ejecutor.setMaxPoolSize(concurrenciaDeEventos);
        ejecutor.setWaitForTasksToCompleteOnShutdown(true);
        ejecutor.setAwaitTerminationMillis(ESPERA_AL_APAGAR.toMillis());
        return ejecutor;
    }

    /** El constructor de Spring Boot ya trae hilos virtuales y el tope de {@code ASYNC_IA_CONCURRENCY_LIMIT}. */
    @Bean(name = EJECUTOR_DE_IA)
    SimpleAsyncTaskExecutor ejecutorDeIa(SimpleAsyncTaskExecutorBuilder simpleAsyncTaskExecutorBuilder) {
        return simpleAsyncTaskExecutorBuilder.threadNamePrefix("ia-").build();
    }

    @Override
    public Executor getAsyncExecutor() {
        return ejecutorDeEventos();
    }
}
