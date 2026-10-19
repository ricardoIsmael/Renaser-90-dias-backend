package com.renaser.os.leadership.infrastructure.adapter.in.scheduler;

import com.renaser.os.leadership.application.ports.in.ResumirConsultasDelLiderUseCase;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.application.ProcesosConInterruptor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cada hora, en el minuto 10, publica el resumen «Tu líder revisó a tu grupo» a los mentores a quienes ahora les toca
 * (las 20:00 de SU zona; spec 005, FR-017).
 *
 * <p>Cada hora y no una vez al día (regla 02): la hora de cada mentor depende de su zona. Cada ventana local de 60
 * minutos contiene exactamente una corrida del minuto 10 UTC, sea cual sea el desfase de la zona. Que la corrida no
 * repita el aviso lo resuelve la clave por mentor y día local, no la frecuencia.
 *
 * <p>Encendido por defecto; la suite lo apaga ({@code renaser.scheduling.resumen-consultas-lider.activo}) para que no
 * dispare en medio de otra prueba, y las pruebas llaman al caso de uso directo.
 */
@Component
@ConditionalOnProperty(name = "renaser.scheduling.resumen-consultas-lider.activo", havingValue = "true",
        matchIfMissing = true)
class ResumirConsultasDelLiderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ResumirConsultasDelLiderScheduler.class);

    private final ResumirConsultasDelLiderUseCase resumir;
    private final Interruptores interruptores;

    ResumirConsultasDelLiderScheduler(ResumirConsultasDelLiderUseCase resumir, Interruptores interruptores) {
        this.resumir = resumir;
        this.interruptores = interruptores;
    }

    @Scheduled(cron = "${renaser.scheduling.resumen-consultas-lider.cron:0 10 * * * *}", zone = "UTC")
    @SchedulerLock(name = "leadership-resumir-consultas",
            lockAtMostFor = "${renaser.scheduling.shedlock.leadership-resumir-consultas.lock-at-most-for:PT10M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.leadership-resumir-consultas.lock-at-least-for:PT30S}")
    public void ejecutar() {
        if (ProcesosConInterruptor.saltaEsteTurno(interruptores, "lider.cumplimiento",
                "leadership.resumir-consultas")) {
            return;
        }
        int avisos = resumir.resumir();
        if (avisos > 0) {
            log.info("[leadership.ResumirConsultasDelLiderScheduler] {} aviso(s) de revision del lider", avisos);
        }
    }
}
