package com.renaser.os.habits.infrastructure.adapter.in.scheduler;

import com.renaser.os.habits.application.ports.in.registro.ExpirarRegistrosVencidosUseCase;
import com.renaser.os.habits.application.ports.in.santuario.ExpirarRachasVencidasUseCase;
import com.renaser.os.habits.application.ports.out.participante.ListarParticipantesActivosPort;
import com.renaser.os.shared.domain.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reemplaza el cron `daily-reset` del repo viejo (paso 0, docs/MODULO_HABITS.md
 * §5): barrido ciego de lo PENDIENTE vencido + rachas sin celular vencidas.
 *
 * <p>Simplificacion deliberada: NO incluye `incrementProgramDaysForTrainees`
 * (dia_programa lo avanza `users`, fuera de este modulo), NI la generacion
 * masiva de tracks del dia siguiente (ver {@code GenerarTracksDelDiaUseCase}
 * — queda para un caso de uso separado, invocado por participante, no por
 * lote global en esta version), NI `reviewPhoneFreeWeeks` (revision semanal
 * con penalizacion — RevisionSemanalSinCelular existe como deuda documentada,
 * sin caso de uso propio en esta pasada).
 */
@Component
class ExpirarRegistrosScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpirarRegistrosScheduler.class);

    private final ExpirarRegistrosVencidosUseCase expirarRegistrosUseCase;
    private final ExpirarRachasVencidasUseCase expirarRachasUseCase;
    private final ListarParticipantesActivosPort listarParticipantesPort;
    private final Clock clock;

    ExpirarRegistrosScheduler(ExpirarRegistrosVencidosUseCase expirarRegistrosUseCase,
                               ExpirarRachasVencidasUseCase expirarRachasUseCase,
                               ListarParticipantesActivosPort listarParticipantesPort, Clock clock) {
        this.expirarRegistrosUseCase = expirarRegistrosUseCase;
        this.expirarRachasUseCase = expirarRachasUseCase;
        this.listarParticipantesPort = listarParticipantesPort;
        this.clock = clock;
    }

    /** 05:00 UTC ~ 00:00 Lima — misma hora que el cron viejo (route.ts:4). */
    @Scheduled(cron = "0 0 5 * * *", zone = "UTC")
    void ejecutar() {
        int registrosExpirados = expirarRegistrosUseCase.expirarPendientesAnterioresA(clock.today());
        int rachasExpiradas = expirarRachasUseCase.expirarVencidas(listarParticipantesPort.todos());
        log.info("Barrido nocturno de habits: {} registros expirados, {} rachas sin celular expiradas",
                registrosExpirados, rachasExpiradas);
    }
}
