package com.renaser.os.mentoring.infrastructure.adapter.in.scheduler;

import com.renaser.os.mentoring.application.ports.in.CerrarHuecosDeLaSemanaUseCase;
import com.renaser.os.mentoring.application.ports.in.CerrarHuecosDeLaSemanaUseCase.ResultadoDelCierre;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Guarda la foto semanal de los huecos de horario (spec 015, FR-018) cuando termina el domingo de cada persona.
 *
 * <p><b>Cada hora y no «el lunes a tal hora UTC»</b>: el domingo de cada persona termina a una hora UTC distinta según
 * su zona (regla 02 §1, E-91). Quien decide con quién hay algo que hacer es el servicio, en la zona de cada una; una
 * corrida donde nadie tiene una semana por cerrar solo lee la página de programas.
 *
 * <p>Minuto 50: el cierre del semáforo ({@code points-cerrar-semaforo}) corre en el 25, el resumen semanal en el 40 y el
 * barrido de cajas en el 45; esta foto no depende de ninguno, pero deja sus lecturas para el final de la hora. A propósito NO se apaga con el
 * interruptor de {@code mentor.mi_grupo}: la foto guarda el horario que regía, y ese dato se sobrescribe; saltarse
 * unas semanas por un mantenimiento las perdería para siempre.
 *
 * <p>El lock evita el trabajo doble entre instancias; la corrección no depende de él (la foto no se duplica: PK y
 * {@code ON CONFLICT DO NOTHING}).
 */
@Component
class CerrarHuecosScheduler {

    private static final Logger log = LoggerFactory.getLogger(CerrarHuecosScheduler.class);

    private final CerrarHuecosDeLaSemanaUseCase cerrarHuecos;

    CerrarHuecosScheduler(CerrarHuecosDeLaSemanaUseCase cerrarHuecos) {
        this.cerrarHuecos = cerrarHuecos;
    }

    @Scheduled(cron = "${renaser.scheduling.huecos-cierre.cron:0 50 * * * *}", zone = "UTC")
    @SchedulerLock(name = "mentoring-cerrar-huecos",
            lockAtMostFor = "${renaser.scheduling.shedlock.mentoring-cerrar-huecos.lock-at-most-for:PT20M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.mentoring-cerrar-huecos.lock-at-least-for:PT30S}")
    public void cerrarPendientes() {
        ResultadoDelCierre resultado = cerrarHuecos.cerrarPendientes();
        if (resultado.fallidas() > 0) {
            log.warn("[mentoring.CerrarHuecosScheduler] {} participantes fallaron (de {}); se reintentan en la proxima corrida",
                    resultado.fallidas(), resultado.evaluados());
        }
        log.info("[mentoring.CerrarHuecosScheduler] evaluados={} fotosGuardadas={}", resultado.evaluados(),
                resultado.guardadas());
    }
}
