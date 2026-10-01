package com.renaser.os.community.infrastructure.adapter.in.scheduler;

import com.renaser.os.community.application.ports.in.celula.DetectarGruposSinMentorUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Barre cada hora los grupos en curso sin mentor y avisa al líder de mentores (D-240).
 *
 * <p>Cada hora y no una vez al día (regla 02): el día del grupo depende de la zona de su cohorte, y
 * un mentor quitado a media mañana se avisa en la hora siguiente y no al otro día. Que la corrida
 * horaria no repita el aviso lo resuelve la clave por grupo y día local, no la frecuencia; que no
 * llegue de madrugada, el umbral de hora de {@code AvisoDeArmadoDeGrupos}.
 *
 * <p>Encendido por defecto; la suite lo apaga ({@code renaser.scheduling.grupos-sin-mentor.activo})
 * para que no dispare en medio de otra prueba, y las pruebas llaman al caso de uso directo.
 */
@Component
@ConditionalOnProperty(name = "renaser.scheduling.grupos-sin-mentor.activo", havingValue = "true", matchIfMissing = true)
class AvisarGruposSinMentorScheduler {

    private static final Logger log = LoggerFactory.getLogger(AvisarGruposSinMentorScheduler.class);

    private final DetectarGruposSinMentorUseCase detectar;

    AvisarGruposSinMentorScheduler(DetectarGruposSinMentorUseCase detectar) {
        this.detectar = detectar;
    }

    @Scheduled(cron = "${renaser.scheduling.grupos-sin-mentor.cron:0 15 * * * *}", zone = "UTC")
    @SchedulerLock(name = "community-avisar-grupos-sin-mentor",
            lockAtMostFor = "${renaser.scheduling.shedlock.community-avisar-grupos-sin-mentor.lock-at-most-for:PT10M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.community-avisar-grupos-sin-mentor.lock-at-least-for:PT30S}")
    public void ejecutar() {
        int avisados = detectar.avisarDeLosQueNoTienenMentor();
        if (avisados > 0) {
            log.info("[community.AvisarGruposSinMentorScheduler] {} grupo(s) en curso sin mentor", avisados);
        }
    }
}
