package com.renaser.os.habits.infrastructure.adapter.in.scheduler;

import com.renaser.os.habits.application.ports.in.aviso.DespacharAvisosHabitoUseCase;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.shared.domain.UserId;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Barrido que emite los dos avisos automaticos de cada habito (pedido del dueno, 2026-09-05).
 *
 * <p><b>Cada 5 minutos, no una vez al dia — y eso no es una eleccion de comodidad</b> (regla
 * 02 seccion 1). El aviso depende de la hora LOCAL del participante, y la medianoche local no
 * existe a una hora UTC fija: un cron diario que dependa del dia del aprendiz esta mal por
 * construccion. La forma correcta es que el cron corra seguido y que el DOMINIO decida si toca
 * ({@code CalculadoraAvisosHabito}). Ademas, "faltan 15 minutos" no se puede resolver con
 * granularidad diaria por definicion.
 *
 * <p><b>Por que 5 y no 1 minuto.</b> El barrido recorre el padron completo y, por participante,
 * consulta sus registros del dia y su catalogo. A 1 minuto eso es 5 veces mas carga permanente
 * de Postgres para ganar 4 minutos de precision en un aviso de cortesia. El costo real de los 5
 * minutos es que un aviso puede llegar hasta 5 minutos antes de la antelacion configurada, nunca
 * despues del momento avisado (la franja de la calculadora es {@code [momento - antelacion,
 * momento)}): se adelanta un poco, no se atrasa. Mismo intervalo que ya usa
 * {@code calendar.GenerarRecordatoriosScheduler}.
 *
 * <p><b>{@code @SchedulerLock} (C-5).</b> Dos instancias sin lock no producen avisos duplicados
 * — la deduplicacion de {@code notificaciones} por {@code origen_evento_id} (C-7/V16) lo impide
 * —, pero si recorren las dos el padron entero y hacen el trabajo doble, igual que el caso ya
 * documentado en {@code GenerarTracksDelDiaScheduler}. El lock evita ese desperdicio.
 *
 * <p><b>Aislamiento por participante</b> (regla 02 seccion 4, mismo espiritu que C-6): cada
 * llamada al caso de uso corre en su propia transaccion y un fallo aislado (una zona horaria
 * invalida, por ejemplo) no detiene el barrido ni revierte lo ya publicado para los demas.
 *
 * <p><b>Desviacion consciente de "un barrido masivo pagina":</b> el puerto de padron
 * ({@code participantesInscritosActivos}) no tiene variante paginada y devuelve solo UUIDs, no
 * filas. Se sigue el precedente exacto de {@code GenerarTracksDelDiaScheduler}, que carga la
 * misma lista por el mismo motivo. Si el padron crece a un tamano en que esa lista pese, hay que
 * agregar paginacion al puerto y los dos barridos se corrigen juntos.
 */
@Component
class DespacharAvisosHabitoScheduler {

    private static final Logger log = LoggerFactory.getLogger(DespacharAvisosHabitoScheduler.class);

    private final DespacharAvisosHabitoUseCase despacharAvisosUseCase;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;

    DespacharAvisosHabitoScheduler(DespacharAvisosHabitoUseCase despacharAvisosUseCase,
                                    ConsultarProgresoParticipanteHabitsPort progresoPort) {
        this.despacharAvisosUseCase = despacharAvisosUseCase;
        this.progresoPort = progresoPort;
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "UTC")
    @SchedulerLock(name = "habits-despachar-avisos",
            lockAtMostFor = "${renaser.scheduling.shedlock.habits-despachar-avisos.lock-at-most-for:PT4M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.habits-despachar-avisos.lock-at-least-for:PT10S}")
    public void ejecutar() {
        List<UserId> participantes = progresoPort.participantesInscritosActivos();
        int avisos = 0;
        int fallidos = 0;
        for (UserId participanteId : participantes) {
            try {
                avisos += despacharAvisosUseCase.despacharDe(participanteId);
            } catch (RuntimeException ex) {
                fallidos++;
                log.warn("[habits.DespacharAvisosHabitoScheduler] no se pudieron despachar los avisos de {}: {}",
                        participanteId, ex.toString());
            }
        }
        if (avisos > 0 || fallidos > 0) {
            log.info("[habits.DespacharAvisosHabitoScheduler] {} aviso(s) publicado(s), {} participante(s) fallido(s)"
                    + " de {}", avisos, fallidos, participantes.size());
        }
    }
}
