package com.renaser.os.habits.infrastructure.adapter.in.scheduler;

import com.renaser.os.habits.application.ports.in.preferencia.PromoverCambiosHorarioProgramadosUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hace regir los cambios de horario que quedaron programados (§12.1, "no se improvisa el dia").
 * Adaptador propio y no una linea mas dentro de {@code ExpirarRegistrosScheduler}: cerrar el dia
 * anterior y abrir la configuracion del dia nuevo son dos responsabilidades distintas, con dos
 * casos de uso distintos (SRP, CLAUDE.MD §5.4.8) — mezclarlas obligaria a que un fallo de una
 * arrastre a la otra.
 *
 * <p><b>E-557 (2026-10-06): cada hora, no a las 04:40 UTC.</b> Antes corria una vez por noche y comparaba la
 * {@code fecha_efectiva} (una fecha en la zona de cada participante) contra la fecha UTC del servidor: para Lima
 * adelantaba el cambio unos 20 minutos (23:40 del dia anterior), para Los Angeles unas 3 horas y para quien esta por
 * delante de UTC lo hacia regir con horas de retraso. La medianoche local no existe a una hora UTC fija (regla 02 §1,
 * la familia de E-91): ahora el cron corre cada hora y {@code PromocionCambioHorarioService} decide por participante,
 * contra SU hoy, a quien ya le toca. Para Lima el cambio pasa a regir a las 05:00 UTC (su medianoche), no a las 04:40.
 *
 * <p><b>El orden con la generacion del dia es por participante, no por cron.</b> Antes se garantizaba con 20 minutos
 * de diferencia entre dos crons, justo el tipo de margen que causo E-91. Ahora {@code GeneracionDeJornadasService}
 * promueve los cambios de ESA persona antes de generarle el dia ({@code promoverLosDe}); este barrido solo cubre a quien
 * ya tenia su dia armado cuando su cambio llego a regir. Ninguna de las dos cosas espera a la otra.
 *
 * <p><b>C-5 (docs/informes/auditoria-fixes/C-5.md): {@code @SchedulerLock}, no opcional.</b>
 * Antes de E-557, {@code promover} leia los pendientes SIN {@code FOR UPDATE} y {@code aplicarAhora}/{@code registrar}
 * (historial) eran INSERT puros: dos instancias que leyeran el mismo pendiente antes de que cualquiera lo borrara
 * APLICABAN el cambio dos veces e INSERTABAN dos filas en {@code historial_cambios_horario}, la tabla que cobra el cupo
 * semanal. Desde E-557 el service borra el pendiente PRIMERO y solo quien lo borro lo cobra (la segunda transaccion
 * espera la fila y no encuentra nada), porque ahora hay dos caminos que promueven. El lock se conserva para no duplicar
 * el barrido entero cada hora.
 */
@Component
class PromoverCambiosHorarioScheduler {

    private static final Logger log = LoggerFactory.getLogger(PromoverCambiosHorarioScheduler.class);

    private final PromoverCambiosHorarioProgramadosUseCase promoverUseCase;

    PromoverCambiosHorarioScheduler(PromoverCambiosHorarioProgramadosUseCase promoverUseCase) {
        this.promoverUseCase = promoverUseCase;
    }

    /**
     * {@code lockAtMostFor} por defecto 15 minutos: cada pendiente se promueve en su propia
     * transacción corta (solo escrituras a Postgres, sin IA ni llamada externa), así que aun
     * con miles de cambios pendientes el barrido real tarda segundos-a-pocos-minutos: 15 min
     * es margen generoso sin dejar el lock preso hasta el próximo intento si el proceso muere a
     * mitad de barrido. La suite lo apaga con {@code renaser.scheduling.promover-cambios-horario.cron: "-"}.
     */
    @Scheduled(cron = "${renaser.scheduling.promover-cambios-horario.cron:0 0 * * * *}", zone = "UTC")
    @SchedulerLock(name = "habits-promover-cambios-horario",
            lockAtMostFor = "${renaser.scheduling.shedlock.habits-promover-cambios-horario.lock-at-most-for:PT15M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.habits-promover-cambios-horario.lock-at-least-for:PT10S}")
    public void ejecutar() {
        int promovidos = promoverUseCase.promoverLosQueYaRigen();
        if (promovidos > 0) {
            log.info("[habits.PromoverCambiosHorarioScheduler] {} cambio(s) de horario programados pasaron a regir",
                    promovidos);
        }
    }
}
