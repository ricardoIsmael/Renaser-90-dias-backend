package com.renaser.os.users.infrastructure.adapter.in.scheduler;

import com.renaser.os.users.application.ports.in.participante.AvanzarDiaProgramaUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * D-66: el cron que faltaba, señalado como bloqueante en docs/MODULO_PHASECONTRACTS.md
 * §0.2 ("nada en el baseline garantiza que un cron la recalcule... ese cron todavia no
 * existe"). {@code @EnableScheduling} ya esta declarado globalmente por `points`
 * (D-P4, `PointsSchedulingConfig`) — no hace falta repetirlo aca.
 *
 * <p>Horario elegido (04:50 UTC): despues de {@code PurgarCuentasBajaScheduler} (04:15)
 * y de {@code notifications.PurgaNotificacionesScheduler} (04:30), y ANTES de
 * {@code habits.ExpirarRegistrosScheduler} (05:00) y {@code points.SnapshotRankingScheduler}
 * (05:05) — el dia/fase de cada participante debe quedar al dia ANTES de que esos dos
 * corran, ya que ambos leen `dia_programa` indirectamente (habits para generar el
 * catalogo del dia nuevo, points para el ranking del dia).
 *
 * <p>La idempotencia real vive en el dominio ({@code ParticipacionPrograma.avanzarDiaDelPrograma},
 * comparando contra {@code dia_programa_avanzado_el} EN LA ZONA DE CADA PARTICIPANTE) —
 * este scheduler no sabe nada de eso, solo dispara el caso de uso y loguea el resultado.
 *
 * <p><b>{@code @SchedulerLock} (C-5), y no es opcional.</b> Esa idempotencia protege contra
 * correr el cron dos veces EN SERIE, pero no contra dos instancias EN PARALELO:
 * {@code RelojProgramaService.avanzarParticipantesActivos} lee la pagina sin bloqueo, decide
 * en memoria y despues guarda, asi que dos instancias pueden leer al mismo participante con
 * {@code dia_programa_avanzado_el} de ayer, ambas decidir que corresponde avanzar, y ambas
 * guardar: <b>el aprendiz saltaria dos dias y se perderia uno del programa</b>. Es el mismo
 * check-then-act de C-2 (roca diaria) y C-12 (primer puntaje). Clasificacion y criterio en
 * docs/informes/auditoria-fixes/C-5.md; todo {@code @Scheduled} nuevo pasa por esa tabla.
 */
@Component
public class AvanzarDiaProgramaScheduler {

    private static final Logger log = LoggerFactory.getLogger(AvanzarDiaProgramaScheduler.class);

    private final AvanzarDiaProgramaUseCase avanzarDiaProgramaUseCase;

    public AvanzarDiaProgramaScheduler(AvanzarDiaProgramaUseCase avanzarDiaProgramaUseCase) {
        this.avanzarDiaProgramaUseCase = avanzarDiaProgramaUseCase;
    }

    @Scheduled(cron = "0 50 4 * * *", zone = "UTC")
    @SchedulerLock(name = "users-avanzar-dia-programa",
            lockAtMostFor = "${renaser.scheduling.shedlock.users-avanzar-dia-programa.lock-at-most-for:PT30M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.users-avanzar-dia-programa.lock-at-least-for:PT30S}")
    public void avanzarDiaDeParticipantesActivos() {
        var resultado = avanzarDiaProgramaUseCase.avanzarParticipantesActivos();
        log.info("[users.AvanzarDiaProgramaScheduler] {} participante(s) evaluado(s), {} avanzado(s)",
                resultado.evaluados(), resultado.avanzados());
    }
}
