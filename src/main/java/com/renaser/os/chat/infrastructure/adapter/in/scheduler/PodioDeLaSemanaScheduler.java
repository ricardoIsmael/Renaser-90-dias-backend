package com.renaser.os.chat.infrastructure.adapter.in.scheduler;

import com.renaser.os.chat.application.ports.in.ranking.PublicarPodioDeLaSemanaUseCase;
import com.renaser.os.chat.application.ports.in.ranking.PublicarPodioDeLaSemanaUseCase.ResultadoDelPodio;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * El podio semanal del ranking general en el grupo general (D-262): los lunes a las 09:00 de Lima.
 *
 * <p><b>Una foto global, a una hora de Lima</b> (como el corte diario del ranking, E-561): el podio es uno solo
 * para todo el grupo, así que el cron va en la zona del padrón ({@code zone = "America/Lima"}) y no por persona.
 * La semana la deriva el dominio del instante en Lima ({@code SemanaDelRanking.ultimaCerradaAl}), nunca de la fecha
 * del servidor.
 *
 * <p><b>Corre cada hora del lunes, de 09:00 a 23:00</b>, no una sola vez: si a las 09:00 el backend estaba caído o
 * S3 falló, la hora siguiente lo publica (regla 02 §2: una corrida que no ocurre no se pierde). Los ids de los
 * mensajes salen de la semana, y si el texto ya está no se hace nada más: correr de más no duplica.
 *
 * <p>{@code @SchedulerLock} (C-5): sin cerrojo, dos instancias dibujarían y subirían la imagen a la vez (el mensaje
 * no se duplicaría por el {@code ON CONFLICT}, pero sería trabajo doble).
 *
 * <p><b>Interruptor</b> {@code renaser.chat.ranking-semanal.activo} ({@code RANKING_SEMANAL_ACTIVO}), apagado por
 * defecto como la bienvenida: apagado no lee nada ni publica. Prenderlo un martes no publica la semana anterior: el
 * próximo podio sale el lunes siguiente (o a pedido de Administración).
 */
@Component
public class PodioDeLaSemanaScheduler {

    private static final Logger log = LoggerFactory.getLogger(PodioDeLaSemanaScheduler.class);

    private final PublicarPodioDeLaSemanaUseCase podio;

    public PodioDeLaSemanaScheduler(PublicarPodioDeLaSemanaUseCase podio,
                                    @Value("${renaser.chat.ranking-semanal.activo:false}") boolean activo) {
        this.podio = podio;
        log.info("[chat.podio] podio semanal en el grupo general: {}", activo ? "PRENDIDO" : "apagado");
    }

    @Scheduled(cron = "${renaser.scheduling.chat-podio-de-la-semana.cron:0 0 9-23 * * MON}", zone = "America/Lima")
    @SchedulerLock(name = "chat-podio-de-la-semana",
            lockAtMostFor = "${renaser.scheduling.shedlock.chat-podio-de-la-semana.lock-at-most-for:PT20M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.chat-podio-de-la-semana.lock-at-least-for:PT30S}")
    public void publicar() {
        try {
            ResultadoDelPodio resultado = podio.publicarLaSemanaCerrada();
            log.debug("[chat.podio] semana del {}: {} ({} piezas)", resultado.lunes(), resultado.estado(),
                    resultado.piezas());
        } catch (RuntimeException e) {
            log.error("[chat.podio] el podio no se pudo publicar; se reintenta en la próxima hora del lunes: {}",
                    e.getMessage(), e);
        }
    }
}
