package com.renaser.os.community.infrastructure.adapter.in.scheduler;

import com.renaser.os.community.application.ports.in.celula.DetectarGruposPorVencerUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Barre una vez al dia los grupos que estan por cerrar su periodo.
 *
 * <p>Corre TODOS los dias de la ventana de aviso, no solo el dia del umbral. Un barrido atado al
 * dia exacto se pierde entero si esa mañana hubo un despliegue o la instancia estaba caida, y
 * nadie se entera de que se perdio. Lo que evita el aviso repetido no es la frecuencia sino la
 * clave de deduplicacion, anclada al grupo y a su fecha de cierre.
 *
 * <p>A las 06:10 en Lima (11:10 UTC): despues de que el administrador empiece su dia, para que el
 * aviso este arriba en su bandeja cuando la abra, y no a las 3 de la mañana enterrado bajo lo
 * demas.
 *
 * <p>Este SI queda encendido, a diferencia de los dos que rotaban: avisar no mueve a nadie. Lo
 * unico que hace es poner la decision delante de quien tiene que tomarla.
 */
@Component
class AvisarGruposPorVencerScheduler {

    private static final Logger log = LoggerFactory.getLogger(AvisarGruposPorVencerScheduler.class);

    private final DetectarGruposPorVencerUseCase detectar;

    AvisarGruposPorVencerScheduler(DetectarGruposPorVencerUseCase detectar) {
        this.detectar = detectar;
    }

    @Scheduled(cron = "${renaser.scheduling.grupos-por-vencer.cron:0 10 11 * * *}", zone = "UTC")
    @SchedulerLock(name = "community-avisar-grupos-por-vencer",
            lockAtMostFor = "${renaser.scheduling.shedlock.community-avisar-grupos-por-vencer.lock-at-most-for:PT10M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.community-avisar-grupos-por-vencer.lock-at-least-for:PT30S}")
    public void ejecutar() {
        int avisados = detectar.avisarDeLosQueVencen();
        if (avisados > 0) {
            log.info("[community.AvisarGruposPorVencerScheduler] {} grupo(s) por vencer", avisados);
        }
    }
}
