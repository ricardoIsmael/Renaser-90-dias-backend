package com.renaser.os.community.infrastructure.adapter.in.scheduler;

import com.renaser.os.community.application.ports.in.acompanamiento.RotarMentoresUseCase;
import com.renaser.os.community.application.ports.in.acompanamiento.RotarMentoresUseCase.ResultadoRotacion;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Rota los mentores de las cohortes cuyo día de anclaje es hoy en SU zona.
 *
 * <p>Corre cada hora y no una vez al día: las cohortes pueden estar en zonas distintas, y una
 * corrida diaria a hora fija rotaría a destiempo en todas menos una. Cada corrida pregunta a la
 * política de cada cohorte si hoy le toca, así que las 23 corridas que no aplican no hacen nada.
 *
 * <p>La repetición es segura porque la clave de operación se deriva de la fecha de anclaje, no
 * de la hora de ejecución: la segunda corrida del día encuentra los cambios ya aplicados y los
 * saltea. El lock solo evita el trabajo duplicado entre instancias; la corrección no depende
 * de él.
 */
/**
 * <b>APAGADO desde 2026-09-11.</b> El cliente cambio el modelo: los grupos ya no rotan solos,
 * los arma el administrador con un periodo (ver `V48` y el CRUD de `/api/v1/admin/cells`). Si
 * este job siguiera corriendo moveria a gente que el admin coloco a mano, que es exactamente lo
 * que se dejo de querer.
 *
 * <p>Se apaga por CONFIGURACION y no se borra el codigo. Es una decision, no una limpieza
 * pendiente: la logica de rotacion esta probada y volver a pedirla es plausible -- ya cambiaron
 * de idea una vez. Encenderlo otra vez es poner `renaser.scheduling.rotacion-mentores.enabled`
 * en true, no rehacerlo.
 */
@Component
@ConditionalOnProperty(name = "renaser.scheduling.rotacion-mentores.enabled", havingValue = "true", matchIfMissing = false)
class RotarMentoresScheduler {

    private static final Logger log = LoggerFactory.getLogger(RotarMentoresScheduler.class);

    private final RotarMentoresUseCase rotarMentores;

    RotarMentoresScheduler(RotarMentoresUseCase rotarMentores) {
        this.rotarMentores = rotarMentores;
    }

    @Scheduled(cron = "${renaser.scheduling.rotacion-mentores.cron:0 20 * * * *}", zone = "UTC")
    @SchedulerLock(name = "community-rotar-mentores",
            lockAtMostFor = "${renaser.scheduling.shedlock.community-rotar-mentores.lock-at-most-for:PT15M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.community-rotar-mentores.lock-at-least-for:PT30S}")
    public void ejecutar() {
        List<ResultadoRotacion> resultados = rotarMentores.rotarLasQueCorresponda();
        for (ResultadoRotacion resultado : resultados) {
            log.info("[community.RotarMentoresScheduler] cohorte {}: {} cambio(s), {} ya aplicado(s), "
                            + "{} sin sustituto, {} sin cobertura",
                    resultado.cohorteId(), resultado.cambiosAplicados(), resultado.yaAplicados(),
                    resultado.sinSustituto().size(), resultado.sinCobertura().size());
        }
    }
}
