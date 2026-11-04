package com.renaser.os.calendar.infrastructure.adapter.in.scheduler;

import com.renaser.os.calendar.application.ports.in.meet.SincronizarSalasDeMeetUseCase;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.application.ProcesosConInterruptor;
import com.renaser.os.shared.domain.Clock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * El proceso en segundo plano de las salas de Meet (spec 040, FR-008, FR-009, FR-017). Dos pasadas:
 * <ul>
 *   <li><b>Cada minuto</b>: reintenta lo que quedó sin hacer con Google y cuyo turno ya llegó (espera exponencial, máximo 8 intentos). Sin cuenta
 *       conectada o con la cuenta revocada no hace ninguna llamada (SC-005).</li>
 *   <li><b>Cada hora</b>: vuelve a calcular las salas de cada evento con Meet automático (regla 02: derivar, no incrementar, y el dominio decide
 *       si toca): las fechas que entraron a la ventana reciben su sala, lo que cambió se corrige solo, avisa si la cuenta sigue revocada (un aviso por
 *       día) y purga lo de más de 12 meses.</li>
 * </ul>
 * Apagada la funcionalidad {@code eventos.meet} (así nace), las dos saltan su turno. La suite las apaga con {@code cron: "-"} y las llama directo.
 */
@Component
class SalasDeMeetScheduler {

    private static final Logger log = LoggerFactory.getLogger(SalasDeMeetScheduler.class);
    private static final String CLAVE = "eventos.meet";

    private final SincronizarSalasDeMeetUseCase sincronizarUseCase;
    private final Interruptores interruptores;
    private final Clock clock;

    SalasDeMeetScheduler(SincronizarSalasDeMeetUseCase sincronizarUseCase, Interruptores interruptores, Clock clock) {
        this.sincronizarUseCase = sincronizarUseCase;
        this.interruptores = interruptores;
        this.clock = clock;
    }

    @Scheduled(cron = "${renaser.scheduling.salas-de-meet-reintentos.cron:0 * * * * *}", zone = "UTC")
    @SchedulerLock(name = "calendar-salas-de-meet-reintentos",
            lockAtMostFor = "${renaser.scheduling.shedlock.calendar-salas-de-meet-reintentos.lock-at-most-for:PT10M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.calendar-salas-de-meet-reintentos.lock-at-least-for:PT20S}")
    void reintentar() {
        if (ProcesosConInterruptor.saltaEsteTurno(interruptores, CLAVE, "calendar.salas-de-meet-reintentos")) {
            return;
        }
        int eventos = sincronizarUseCase.reintentarLoPendiente(clock.now());
        if (eventos > 0) {
            log.info("[calendar.SalasDeMeetScheduler] {} evento(s) con salas de Meet reintentado(s)", eventos);
        }
    }

    @Scheduled(cron = "${renaser.scheduling.salas-de-meet-mantenimiento.cron:0 10 * * * *}", zone = "UTC")
    @SchedulerLock(name = "calendar-salas-de-meet-mantenimiento",
            lockAtMostFor = "${renaser.scheduling.shedlock.calendar-salas-de-meet-mantenimiento.lock-at-most-for:PT30M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.calendar-salas-de-meet-mantenimiento.lock-at-least-for:PT1M}")
    void mantener() {
        if (ProcesosConInterruptor.saltaEsteTurno(interruptores, CLAVE, "calendar.salas-de-meet-mantenimiento")) {
            return;
        }
        sincronizarUseCase.mantenimiento(clock.now());
    }
}
