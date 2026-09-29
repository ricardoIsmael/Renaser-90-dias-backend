package com.renaser.os.chat.infrastructure.adapter.in.scheduler;

import com.renaser.os.chat.application.ports.in.semaforo.EnviarTarjetasDelSemaforoUseCase;
import com.renaser.os.chat.application.ports.in.semaforo.EnviarTarjetasDelSemaforoUseCase.ResultadoDeTarjetas;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * La tarjeta diaria del semáforo en el soporte (D-223). Corre CADA 5 MINUTOS y no a una hora fija: las
 * 23:50 de un aprendiz son una hora UTC distinta según su zona (regla 02 §1, E-91), así que el dominio
 * ({@code HoraDeLaTarjeta}) decide con quién toca. En Lima caen dos corridas en la ventana, 23:50 y 23:55:
 * la segunda es el reintento, y el id calculado de cada mensaje hace que no se duplique.
 *
 * <p>{@code @SchedulerLock} (C-5): sin cerrojo, dos instancias recorrerían el padrón a la vez; el mensaje
 * no se duplicaría ({@code ON CONFLICT DO NOTHING} sobre su id), pero sería trabajo doble. {@code lockAtMostFor}
 * por debajo del intervalo: si el proceso muere con el cerrojo tomado, no se pierde la corrida de las 23:55.
 *
 * <p><b>Interruptor</b> {@code renaser.semaforo.tarjeta-diaria.activa}: el default del código es
 * {@code false}, así que las pruebas (su {@code application.yaml} reemplaza al de main) no mandan nada solas
 * aunque el cron corra; {@code application.yaml} de main lo prende con {@code SEMAFORO_TARJETA_DIARIA_ACTIVA:true},
 * porque lo pidió el dueño con textos e imágenes ya aprobados. Apagarlo en producción:
 * {@code /renaser/prod/SEMAFORO_TARJETA_DIARIA_ACTIVA=false} y reiniciar. Prenderlo no manda tarjetas
 * atrasadas: solo la del día que está en su 23:50.
 */
@Component
public class TarjetasDelSemaforoScheduler {

    private static final Logger log = LoggerFactory.getLogger(TarjetasDelSemaforoScheduler.class);

    private final EnviarTarjetasDelSemaforoUseCase tarjetas;
    private final boolean activa;

    public TarjetasDelSemaforoScheduler(EnviarTarjetasDelSemaforoUseCase tarjetas,
                                        @Value("${renaser.semaforo.tarjeta-diaria.activa:false}") boolean activa) {
        this.tarjetas = tarjetas;
        this.activa = activa;
        log.info("[chat.semaforo] tarjeta diaria del semáforo en el soporte: {}", activa ? "PRENDIDA" : "apagada");
    }

    @Scheduled(cron = "${renaser.scheduling.semaforo-tarjeta-diaria.cron:0 */5 * * * *}", zone = "UTC")
    @SchedulerLock(name = "chat-tarjetas-del-semaforo",
            lockAtMostFor = "${renaser.scheduling.shedlock.chat-tarjetas-del-semaforo.lock-at-most-for:PT4M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.chat-tarjetas-del-semaforo.lock-at-least-for:PT10S}")
    public void enviarLasQueTocan() {
        if (!activa) {
            return;
        }
        ResultadoDeTarjetas resultado = tarjetas.enviarLasQueTocan();
        if (resultado.enSuHora() == 0) {
            return;
        }
        if (resultado.fallidas() > 0) {
            log.warn("[chat.semaforo] {} tarjetas fallaron (de {} aprendices en su hora); se reintentan en la corrida siguiente si sigue su ventana",
                    resultado.fallidas(), resultado.enSuHora());
        }
        log.info("[chat.semaforo] enSuHora={} enviadas={} fallidas={}",
                resultado.enSuHora(), resultado.enviadas(), resultado.fallidas());
    }
}
