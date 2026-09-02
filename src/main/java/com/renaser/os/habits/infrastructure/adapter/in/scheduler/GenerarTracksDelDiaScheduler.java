package com.renaser.os.habits.infrastructure.adapter.in.scheduler;

import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.shared.domain.UserId;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * El barrido que faltaba: hasta este cambio, los tracks del dia SOLO se generaban al
 * consultar {@code GET /api/v1/habit-tracks/today} (v&iacute;a
 * {@code TracksDelDiaProyeccionService.consultar} &rarr;
 * {@code GenerarTracksDelDiaUseCase.generarDisponiblesAhora}). Un participante que nunca
 * abre la app nunca tendr&iacute;a tracks; sin tracks no hay nada que
 * {@code ExpirarRegistrosScheduler} pueda marcar vencido; sin expiraci&oacute;n, su
 * coherencia queda intacta en 100 &mdash; no abrir la app ser&iacute;a la mejor estrategia
 * para no perder puntos. Este scheduler pre-genera la jornada completa de todos los
 * participantes activos antes de que el dia empiece para ellos, para que ese hueco no
 * exista.
 *
 * <p><b>Horario elegido: 05:02 UTC.</b> Corre despues de
 * {@code users.AvanzarDiaProgramaScheduler} (04:50 UTC) porque necesita el
 * {@code dia_programa} ya avanzado &mdash; el catalogo del dia se resuelve con
 * {@code HorarioHabito.aplicaEnDia(diaPrograma, tipoDia)}, y generar con el dia de programa
 * de AYER generaria el catalogo equivocado. El resto del publico de este programa es de
 * Peru (America/Lima, UTC-5 fijo, sin horario de verano) &mdash; con el cron a las 05:02 UTC,
 * la hora local en Lima es las 00:02, es decir DESPUES de la medianoche local: cuando
 * {@link GenerarTracksDelDiaUseCase#generarDiaCompletoEnSuZona} resuelve
 * {@code LocalDate.now(zonaDelParticipante)}, ya devuelve la fecha del dia que arranca, no
 * la de ayer. Correrlo antes de las 05:00 UTC (medianoche en Lima) haria que esa fecha
 * calculada en la zona del participante siguiera siendo AYER (ver el analisis completo en
 * {@code docs/informes/habits-barrido-nocturno.md} &sect; zonas horarias).
 *
 * <p><b>{@code @SchedulerLock} (C-5, docs/informes/auditoria-fixes/C-5.md), y no es
 * opcional.</b> Aunque {@code registros_habito} tiene {@code UNIQUE (participante_id,
 * habito_id, fecha_ejecucion)} (V1) &mdash; asi que dos instancias generando al mismo
 * participante en paralelo NO pueden duplicar una fila, la segunda choca contra el
 * constraint y esa fila individual queda para el proximo barrido &mdash;, sin lock las DOS
 * instancias igual recorren el padron completo y llaman al caso de uso para CADA
 * participante, duplicando el trabajo (y, el dia que haya IA real en el camino de generacion,
 * duplicando tambien ese costo). Mismo patron de nombre de propiedad y de javadoc que
 * {@code evidence.ProcesarColaValidacionScheduler} y {@code users.AvanzarDiaProgramaScheduler}.
 *
 * <p>Aislamiento por participante (mismo espiritu que C-6, ver
 * {@code RegistroService.expirarPendientesAnterioresA}): un participante con datos
 * corruptos (p.ej. zona horaria invalida) no puede tumbar el barrido de los demas. Cada
 * llamada al caso de uso corre en su propia transaccion (el metodo de la interfaz es
 * {@code @Transactional} por participante), asi que un fallo aislado no revierte lo ya
 * generado para otros.
 */
@Component
class GenerarTracksDelDiaScheduler {

    private static final Logger log = LoggerFactory.getLogger(GenerarTracksDelDiaScheduler.class);

    private final GenerarTracksDelDiaUseCase generarTracksUseCase;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;

    GenerarTracksDelDiaScheduler(GenerarTracksDelDiaUseCase generarTracksUseCase,
                                  ConsultarProgresoParticipanteHabitsPort progresoPort) {
        this.generarTracksUseCase = generarTracksUseCase;
        this.progresoPort = progresoPort;
    }

    @Scheduled(cron = "0 2 5 * * *", zone = "UTC")
    @SchedulerLock(name = "habits-generar-tracks-del-dia",
            lockAtMostFor = "${renaser.scheduling.shedlock.habits-generar-tracks-del-dia.lock-at-most-for:PT30M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.habits-generar-tracks-del-dia.lock-at-least-for:PT30S}")
    public void ejecutar() {
        List<UserId> participantes = progresoPort.participantesInscritosActivos();
        int procesados = 0;
        int fallidos = 0;
        for (UserId participanteId : participantes) {
            try {
                generarTracksUseCase.generarDiaCompletoEnSuZona(participanteId);
                procesados++;
            } catch (RuntimeException ex) {
                fallidos++;
                log.warn("[habits.GenerarTracksDelDiaScheduler] no se pudieron generar los tracks de {}: {}",
                        participanteId, ex.toString());
            }
        }
        log.info(
                "[habits.GenerarTracksDelDiaScheduler] barrido nocturno: {} participante(s) procesado(s), "
                        + "{} fallido(s) de {} candidato(s)",
                procesados, fallidos, participantes.size());
    }
}
