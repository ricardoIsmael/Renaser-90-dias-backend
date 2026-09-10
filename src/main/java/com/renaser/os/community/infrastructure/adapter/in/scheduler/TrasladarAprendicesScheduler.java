package com.renaser.os.community.infrastructure.adapter.in.scheduler;

import com.renaser.os.community.application.ports.in.acompanamiento.TrasladarAprendicesUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Pasa de recepción a grupo estable a quien le corresponde según su día local.
 *
 * <p>Existe para que el traslado NO dependa de que el aprendiz abra la app (plan.md §4): quien
 * no entra el día 4 igual pasa al grupo, y encuentra a sus compañeros cuando vuelve.
 *
 * <p>Corre cada hora por la misma razón que la rotación: cada participante tiene su propia zona
 * y su propio día. La elegibilidad se recalcula desde las fechas, nunca sumando un día por
 * ejecución — si el job estuvo caído una semana, al volver ubica a cada uno donde le toca hoy,
 * sin inventar los pasos intermedios.
 */
/**
 * <b>APAGADO desde 2026-09-11.</b> El cliente cambio el modelo: los aprendices ya no se trasladan solos,
 * los arma el administrador con un periodo (ver `V48` y el CRUD de `/api/v1/admin/cells`). Si
 * este job siguiera corriendo moveria a gente que el admin coloco a mano, que es exactamente lo
 * que se dejo de querer.
 *
 * <p>Se apaga por CONFIGURACION y no se borra el codigo. Es una decision, no una limpieza
 * pendiente: la logica de traslado esta probada y volver a pedirla es plausible -- ya cambiaron
 * de idea una vez. Encenderlo otra vez es poner `renaser.scheduling.traslado-aprendices.enabled`
 * en true, no rehacerlo.
 */
@Component
@ConditionalOnProperty(name = "renaser.scheduling.traslado-aprendices.enabled", havingValue = "true", matchIfMissing = false)
class TrasladarAprendicesScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrasladarAprendicesScheduler.class);

    private final TrasladarAprendicesUseCase trasladarAprendices;
    private final int tamanoLote;

    TrasladarAprendicesScheduler(TrasladarAprendicesUseCase trasladarAprendices,
                                  @Value("${renaser.scheduling.traslado-aprendices.tamano-lote:200}") int tamanoLote) {
        this.trasladarAprendices = trasladarAprendices;
        this.tamanoLote = tamanoLote;
    }

    @Scheduled(cron = "${renaser.scheduling.traslado-aprendices.cron:0 35 * * * *}", zone = "UTC")
    @SchedulerLock(name = "community-trasladar-aprendices",
            lockAtMostFor = "${renaser.scheduling.shedlock.community-trasladar-aprendices.lock-at-most-for:PT20M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.community-trasladar-aprendices.lock-at-least-for:PT30S}")
    public void ejecutar() {
        int movidos = trasladarAprendices.procesarLote(tamanoLote);
        if (movidos > 0) {
            log.info("[community.TrasladarAprendicesScheduler] {} aprendiz(ces) ubicado(s)", movidos);
        }
    }
}
