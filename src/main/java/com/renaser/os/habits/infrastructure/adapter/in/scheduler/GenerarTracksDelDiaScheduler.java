package com.renaser.os.habits.infrastructure.adapter.in.scheduler;

import com.renaser.os.habits.application.ports.in.registro.GenerarJornadasDelDiaUseCase;
import com.renaser.os.habits.application.ports.in.registro.GenerarJornadasDelDiaUseCase.ResultadoDelBarrido;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * El barrido que arma el dia de cada participante antes de que lo abra: hasta que existio, los tracks del dia SOLO se
 * generaban al consultar {@code GET /api/v1/habit-tracks/today}, asi que quien nunca abria la app nunca tenia tracks;
 * sin tracks no hay nada que {@code ExpirarRegistrosScheduler} pueda marcar vencido, y no abrir la app seria la mejor
 * estrategia para no perder puntos. Este barrido pre-genera la jornada completa de todos los participantes activos
 * cuando empieza SU dia.
 *
 * <p><b>E-556 (2026-10-06): cada hora, no una vez a las 05:02 UTC.</b> Esa hora es la medianoche de Lima y de nadie
 * mas (regla 02 §1, la familia de E-91): alguien en Los Angeles recibia su dia a las 21:02 de la vispera y alguien en
 * Tokio a las 14:02 del dia mismo. Ahora el cron corre cada hora, en el minuto 2, y
 * {@code GeneracionDeJornadasService} decide por participante, en su zona, a quien ya le empezo el dia y todavia no lo
 * tiene armado. Para Lima la corrida de las 05:02 UTC es la que arma su dia, igual que antes; las otras 23 no
 * encuentran nada que hacer. El minuto 2 se conserva: en una zona de hora entera el dia se arma dos minutos despues de
 * su medianoche, con lo de ayer ya vencido por {@code ExpirarRegistrosScheduler} (minuto 0).
 *
 * <p><b>Ya no hay dependencia de orden entre crons.</b> El {@code dia_programa} lo deriva {@code users} de las fechas
 * (quien lo lea tarde lo lee bien) y los cambios de horario que rigen hoy los promueve el propio caso de uso, por
 * participante, antes de generarle el dia.
 *
 * <p><b>{@code @SchedulerLock} (C-5, docs/informes/auditoria-fixes/C-5.md), y no es opcional.</b> Aunque
 * {@code registros_habito} tiene {@code UNIQUE (participante_id, habito_id, fecha_ejecucion)} (V1) y dos instancias no
 * pueden duplicar una fila, sin lock las DOS recorren el padron completo cada hora, duplicando el trabajo (y, el dia que
 * haya IA real en el camino de generacion, ese costo). Mismo patron de nombre de propiedad que
 * {@code evidence.ProcesarColaValidacionScheduler} y {@code users.AvanzarDiaProgramaScheduler}. La suite lo apaga con
 * {@code renaser.scheduling.generar-tracks-del-dia.cron: "-"} y lo prueba llamandolo directo.
 *
 * <p>Aislamiento por participante (C-6): quien tiene datos corruptos (p.ej. zona horaria invalida) no puede tumbar el
 * barrido de los demas.
 */
@Component
class GenerarTracksDelDiaScheduler {

    private static final Logger log = LoggerFactory.getLogger(GenerarTracksDelDiaScheduler.class);

    private final GenerarJornadasDelDiaUseCase generarJornadasUseCase;

    GenerarTracksDelDiaScheduler(GenerarJornadasDelDiaUseCase generarJornadasUseCase) {
        this.generarJornadasUseCase = generarJornadasUseCase;
    }

    @Scheduled(cron = "${renaser.scheduling.generar-tracks-del-dia.cron:0 2 * * * *}", zone = "UTC")
    @SchedulerLock(name = "habits-generar-tracks-del-dia",
            lockAtMostFor = "${renaser.scheduling.shedlock.habits-generar-tracks-del-dia.lock-at-most-for:PT30M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.habits-generar-tracks-del-dia.lock-at-least-for:PT30S}")
    public void ejecutar() {
        ResultadoDelBarrido resultado = generarJornadasUseCase.generarLasQueYaEmpezaron();
        if (resultado.generados() > 0 || resultado.fallidos() > 0) {
            log.info("[habits.GenerarTracksDelDiaScheduler] barrido horario: {} participante(s) con el dia recien "
                    + "armado, {} fallido(s) de {} revisado(s)", resultado.generados(), resultado.fallidos(),
                    resultado.participantes());
        } else {
            log.debug("[habits.GenerarTracksDelDiaScheduler] barrido horario: nadie con el dia por armar ({} revisados)",
                    resultado.participantes());
        }
    }
}
