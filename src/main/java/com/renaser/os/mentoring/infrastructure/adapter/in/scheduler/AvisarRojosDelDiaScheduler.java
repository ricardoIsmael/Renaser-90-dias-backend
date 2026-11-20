package com.renaser.os.mentoring.infrastructure.adapter.in.scheduler;

import com.renaser.os.mentoring.application.ports.in.AvisarRojosDelDiaUseCase;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.application.ProcesosConInterruptor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cada 30 minutos (minutos 0 y 30 UTC) avisa a los mentores a quienes ahora les toca —de las 19:00 a las 19:29 de SU zona— de los aprendices de
 * su grupo que hoy están en rojo (spec 057, FR-031).
 *
 * <p>Cada media hora y no una vez al día (regla 02): la hora de cada mentor depende de su zona. Cada ventana local de 30 minutos contiene
 * exactamente una corrida, sea cual sea el desfase de la zona (también las de media hora y 45 minutos). Que una corrida repetida no avise dos
 * veces lo resuelve la clave por mentor, aprendiz y semana, no la frecuencia: la bandeja descarta la repetición.
 *
 * <p>Encendido por defecto, con el interruptor {@code mentoring.aviso-de-rojo} (la funcionalidad {@code mentor.mi_grupo}); la suite lo apaga
 * ({@code renaser.scheduling.aviso-de-rojo.activo}) y las pruebas llaman al caso de uso directo.
 */
@Component
@ConditionalOnProperty(name = "renaser.scheduling.aviso-de-rojo.activo", havingValue = "true", matchIfMissing = true)
class AvisarRojosDelDiaScheduler {

    private static final Logger log = LoggerFactory.getLogger(AvisarRojosDelDiaScheduler.class);

    private final AvisarRojosDelDiaUseCase avisar;
    private final Interruptores interruptores;

    AvisarRojosDelDiaScheduler(AvisarRojosDelDiaUseCase avisar, Interruptores interruptores) {
        this.avisar = avisar;
        this.interruptores = interruptores;
    }

    @Scheduled(cron = "${renaser.scheduling.aviso-de-rojo.cron:0 0,30 * * * *}", zone = "UTC")
    @SchedulerLock(name = "mentoring-aviso-de-rojo",
            lockAtMostFor = "${renaser.scheduling.shedlock.mentoring-aviso-de-rojo.lock-at-most-for:PT10M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.mentoring-aviso-de-rojo.lock-at-least-for:PT30S}")
    public void ejecutar() {
        if (ProcesosConInterruptor.saltaEsteTurno(interruptores, "mentor.mi_grupo", "mentoring.aviso-de-rojo")) {
            return;
        }
        int avisos = avisar.avisar();
        if (avisos > 0) {
            log.info("[mentoring.AvisarRojosDelDiaScheduler] {} aviso(s) de aprendiz en rojo publicado(s)", avisos);
        }
    }
}
