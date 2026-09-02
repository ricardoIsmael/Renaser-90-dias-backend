package com.renaser.os.rag.infrastructure.adapter.in.scheduler;

import com.renaser.os.rag.application.ports.in.espejosombra.GenerarInformeEspejoSombraUseCase;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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
 *
 * <p><b>C-5 (docs/informes/auditoria-fixes/C-5.md): {@code @SchedulerLock}, no opcional.</b>
 * {@code EspejoSombraService.generar} es un check-then-act SIN lock ("¿ya existe informe
 * para esta semana? si no, generalo") protegido solo por la {@code UNIQUE
 * (participante_id, semana_inicio)} de {@code informes_espejo_sombra}: con dos instancias
 * corriendo el mismo barrido semanal, ambas iteran la MISMA lista de participantes casi en
 * paralelo y compiten en (potencialmente) cada uno — el constraint evita que se guarde un
 * informe duplicado (la segunda escritura falla y el {@code try/catch} por participante del
 * barrido la absorbe como error), pero NO evita que {@code com.renaser.os.rag.application.ports.out.ia.GenerarInsightSemanalPort} (la
 * llamada a IA, hoy NoOp — CLAUDE.MD §7) se dispare dos veces por participante. El día que
 * haya un proveedor real ahí, correr con N instancias sin este lock duplica el costo/latencia
 * de IA de todo el barrido semanal cada lunes.
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

    /**
     * Lunes 03:00 UTC — después de que ya cerró por completo la semana anterior.
     *
     * <p>{@code lockAtMostFor} por defecto 6 horas: peor caso estimado con la base de
     * participantes activos de hoy y la latencia de 45s calibrada para IA en otros puntos
     * del repo (CLAUDE.MD §7) — sin un proveedor de IA real conectado todavía (NoOp), es una
     * cota conservadora, no medida; hay que revisarla con datos reales apenas
     * {@code GenerarInsightSemanalPort} deje de ser NoOp (ver "Riesgos" en
     * docs/informes/auditoria-fixes/C-5.md). Un valor alto acá es barato: el próximo intento
     * de este cron es recién el lunes siguiente.
     */
    @Scheduled(cron = "0 0 3 * * MON", zone = "UTC")
    @SchedulerLock(name = "rag-generar-informes-semanales",
            lockAtMostFor = "${renaser.scheduling.shedlock.rag-generar-informes-semanales.lock-at-most-for:PT6H}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.rag-generar-informes-semanales.lock-at-least-for:PT1M}")
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
