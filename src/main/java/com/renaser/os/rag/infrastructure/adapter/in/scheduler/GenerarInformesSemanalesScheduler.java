package com.renaser.os.rag.infrastructure.adapter.in.scheduler;

import com.renaser.os.rag.application.ports.in.espejosombra.GenerarInformeEspejoSombraUseCase;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Barrido semanal del Espejo Sombra: genera el informe de la SEMANA PASADA (lunes a
 * domingo anterior al día de corrida) para todos los participantes activos.
 *
 * <p><b>Decisión sobre la cadencia</b> (pregunta abierta en docs/MODULO_RAG.md §6,
 * punto 4): barrido semanal para todos, no por aniversario individual de cada
 * aprendiz. Es la opción más simple — no requiere calcular el día N de programa de
 * cada participante para decidir cuándo le toca — y es consistente con el resto del
 * dominio (RabbitMQ/Kafka aparte, CLAUDE.MD §5.2 ya prefiere lo simple hasta que se
 * demuestre necesario). Si el negocio confirma que debe ser por aniversario, el
 * cambio queda acotado a este scheduler.
 *
 * <p>Que un participante falle no debe tumbar el barrido de los demás — mismo patrón
 * que {@code rocks.VerdugoService.resolverPendientesDe}: try/catch por participante,
 * log y seguir. {@code @EnableScheduling} ya está declarado globalmente por
 * {@code points} (D-P4, {@code PointsSchedulingConfig}) — no hace falta repetirlo acá.
 */
@Component
public class GenerarInformesSemanalesScheduler {

    private static final Logger log = LoggerFactory.getLogger(GenerarInformesSemanalesScheduler.class);

    private final GenerarInformeEspejoSombraUseCase generarUseCase;
    private final ParticipacionProgramaFinder participacionFinder;
    private final Clock clock;

    public GenerarInformesSemanalesScheduler(GenerarInformeEspejoSombraUseCase generarUseCase,
                                              ParticipacionProgramaFinder participacionFinder, Clock clock) {
        this.generarUseCase = generarUseCase;
        this.participacionFinder = participacionFinder;
        this.clock = clock;
    }

    /** Lunes 03:00 UTC — después de que ya cerró por completo la semana anterior. */
    @Scheduled(cron = "0 0 3 * * MON", zone = "UTC")
    public void generarInformesDeLaSemanaPasada() {
        LocalDate semanaPasadaInicio = inicioDeSemanaPasada(clock.today());
        List<UserId> participantes = participacionFinder.participantesInscritosActivos();
        for (UserId participanteId : participantes) {
            generarSinTumbarElBarrido(participanteId, semanaPasadaInicio);
        }
    }

    private void generarSinTumbarElBarrido(UserId participanteId, LocalDate semanaInicio) {
        try {
            generarUseCase.generar(participanteId, semanaInicio);
        } catch (RuntimeException e) {
            log.error("[rag.GenerarInformesSemanalesScheduler] fallo generando informe. participante={}: {}",
                    participanteId, e.getMessage(), e);
        }
    }

    private static LocalDate inicioDeSemanaPasada(LocalDate hoy) {
        LocalDate inicioSemanaActual = hoy.minusDays(hoy.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
        return inicioSemanaActual.minusWeeks(1);
    }
}
