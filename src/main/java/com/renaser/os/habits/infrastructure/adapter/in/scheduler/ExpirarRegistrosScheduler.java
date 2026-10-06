package com.renaser.os.habits.infrastructure.adapter.in.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.renaser.os.habits.application.ports.in.registro.ExpirarRegistrosVencidosUseCase;
import com.renaser.os.habits.application.ports.in.registro.ExpirarRegistrosVencidosUseCase.ResultadoDelBarrido;
import com.renaser.os.habits.application.ports.in.santuario.ExpirarRachasVencidasUseCase;
import com.renaser.os.habits.application.ports.out.participante.ListarParticipantesActivosPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reemplaza el cron `daily-reset` del repo viejo (paso 0, docs/MODULO_HABITS.md §5): expira lo PENDIENTE de los dias
 * que terminaron y las rachas sin celular vencidas.
 *
 * <p><b>E-534 (2026-10-05): dos corridas, no una.</b> Antes las dos cosas corrian juntas a las 05:00 UTC, y los
 * registros vencian con la fecha UTC de ese instante: la medianoche de Lima y de nadie mas. Ahora:
 * <ul>
 *   <li><b>Registros: cada hora, en el minuto 0</b> ({@link #expirarRegistrosDeDiasTerminados}). El dominio decide con
 *   quien ({@code CorteDeExpiracion}, el dia local de cada participante): la medianoche local no existe a una hora UTC
 *   fija (regla 02 §1). Para Lima la corrida de las 05:00 UTC es la que vence, igual que antes; las otras 23 no
 *   encuentran nada que hacer. Minuto 0 a proposito: en una zona de hora entera el dia se vence justo a su medianoche,
 *   y el semaforo (minuto 25) y la generacion del dia (05:02) siguen viendo lo de ayer ya vencido.</li>
 *   <li><b>Rachas sin celular: igual que siempre, 05:00 UTC</b> ({@link #expirarRachasSinCelularVencidas}). Su
 *   vencimiento es un INSTANTE (24 h + extension desde que empezo), no un dia local, asi que E-534 no la toca; correrla
 *   cada hora cambiaria cuando se liberan para todo el padron, y eso no se pidio.</li>
 * </ul>
 *
 * <p>Simplificacion deliberada (sin cambios): NO incluye `incrementProgramDaysForTrainees` (dia_programa lo avanza
 * `users`), NI la generacion de tracks ({@code GenerarTracksDelDiaScheduler}), NI `reviewPhoneFreeWeeks`.
 *
 * <p>{@code @SchedulerLock} en las dos (C-5): sin cerrojo, dos instancias recorren el mismo padron a la vez. El dato no
 * se dañaria (expirar es un UPDATE idempotente hacia el mismo valor), pero es trabajo doble.
 */
@Component
class ExpirarRegistrosScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpirarRegistrosScheduler.class);

    private final ExpirarRegistrosVencidosUseCase expirarRegistrosUseCase;
    private final ExpirarRachasVencidasUseCase expirarRachasUseCase;
    private final ListarParticipantesActivosPort listarParticipantesPort;

    ExpirarRegistrosScheduler(ExpirarRegistrosVencidosUseCase expirarRegistrosUseCase,
                               ExpirarRachasVencidasUseCase expirarRachasUseCase,
                               ListarParticipantesActivosPort listarParticipantesPort) {
        this.expirarRegistrosUseCase = expirarRegistrosUseCase;
        this.expirarRachasUseCase = expirarRachasUseCase;
        this.listarParticipantesPort = listarParticipantesPort;
    }

    /** La suite de pruebas lo apaga con {@code renaser.scheduling.expirar-registros.cron: "-"} y lo llama directo. */
    @Scheduled(cron = "${renaser.scheduling.expirar-registros.cron:0 0 * * * *}", zone = "UTC")
    @SchedulerLock(name = "habits-expirar-registros",
            lockAtMostFor = "${renaser.scheduling.shedlock.habits-expirar-registros.lock-at-most-for:PT10M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.habits-expirar-registros.lock-at-least-for:PT30S}")
    public void expirarRegistrosDeDiasTerminados() {
        ResultadoDelBarrido resultado = expirarRegistrosUseCase.expirarDiasTerminados();
        if (resultado.expirados() > 0 || resultado.fallidos() > 0) {
            log.info("[habits] barrido de expiracion: {} registro(s) expirado(s), {} fallido(s), {} participante(s) "
                    + "con pendientes", resultado.expirados(), resultado.fallidos(), resultado.participantes());
        } else {
            log.debug("[habits] barrido de expiracion: nada vencido ({} participante(s) con pendientes)",
                    resultado.participantes());
        }
    }

    /** 05:00 UTC ~ 00:00 Lima — la hora de siempre (route.ts:4). */
    @Scheduled(cron = "0 0 5 * * *", zone = "UTC")
    @SchedulerLock(name = "habits-expirar-rachas-sin-celular",
            lockAtMostFor = "${renaser.scheduling.shedlock.habits-expirar-rachas-sin-celular.lock-at-most-for:PT10M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.habits-expirar-rachas-sin-celular.lock-at-least-for:PT30S}")
    public void expirarRachasSinCelularVencidas() {
        int rachasExpiradas = expirarRachasUseCase.expirarVencidas(listarParticipantesPort.todos());
        log.info("[habits] barrido nocturno: {} racha(s) sin celular expirada(s)", rachasExpiradas);
    }
}
