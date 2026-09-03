package com.renaser.os.shared.infrastructure.event;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * C-7 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html): "Outbox sin
 * republicacion al reiniciar, sin limpieza y con listeners no idempotentes".
 *
 * <p>{@code spring.modulith.events.republish-outstanding-events-on-restart=true} (ver
 * application.yaml) cubre la republicacion al ARRANCAR el proceso, pero este backend corre
 * siempre-arriba (CLAUDE.MD §1) -- entre un deploy y el siguiente pueden pasar dias. Si un
 * listener falla de forma transitoria (una caida corta de Postgres, un error momentaneo en
 * otro modulo) sin que el proceso se reinicie, esa publicacion queda incompleta hasta el
 * proximo restart. Este scheduler cierra esa ventana: reintenta periodicamente las
 * publicaciones incompletas mas viejas que {@code renaser.eventos.reintento-tras}, usando el
 * mismo mecanismo de reintento que Modulith ya expone para el arranque
 * ({@link IncompleteEventPublications}), sin esperar a un restart.
 *
 * <p><b>Los dos metodos llevan {@code @SchedulerLock} (C-5), y no es opcional.</b> Este
 * scheduler nacio con el arreglo de C-7, DESPUES de que C-5 clasificara los schedulers
 * existentes, asi que quedo fuera de esa clasificacion: sin lock, con N instancias las N
 * reintentarian las mismas publicaciones incompletas cada 5 minutos, multiplicando por N el
 * trabajo que el outbox esta tratando de recuperar. Que los listeners sean idempotentes
 * evita el DANO, no el DESPERDICIO. Regla general para quien agregue un {@code @Scheduled}
 * nuevo: pasa primero por la tabla de docs/informes/auditoria-fixes/C-5.md y decide si lo
 * necesita -- 3 de los 10 que habia lo necesitaban, no es automatico que si ni que no.
 *
 * <p>Es seguro reintentar automaticamente porque los listeners que consumen estos eventos ya
 * son idempotentes (C-7: {@code notificaciones_origen_evento_uk} para los 4 listeners de
 * `notifications`; los 2 de `chat` ya eran check-then-act idempotentes). El umbral de 5
 * minutos por defecto es deliberadamente mayor a cualquier ejecucion normal de un listener
 * (que hoy son solo escrituras a Postgres, sin llamadas a IA) para no reintentar algo que
 * simplemente esta lento, no fallado.
 *
 * <p>{@code renaser.eventos.retencion-completados} es la limpieza del otro lado: con
 * {@code spring.modulith.events.completion-mode=DELETE} (ver application.yaml) una publicacion
 * completada se borra SOLA apenas se completa, asi que en el camino feliz esta purga no
 * encuentra nada que borrar -- se deja igual como red de seguridad para cualquier entorno que
 * en el futuro vuelva al modo por defecto (UPDATE, que solo marca {@code completion_date} y
 * deja la fila).
 *
 * <p>{@code @EnableScheduling} ya esta declarado globalmente por `points`
 * (D-P4, `PointsSchedulingConfig`) -- no hace falta repetirlo aca (mismo criterio que
 * `notifications.PurgaNotificacionesScheduler`).
 */
@Component
public class EventPublicationMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(EventPublicationMaintenanceScheduler.class);

    private final IncompleteEventPublications incompleteEventPublications;
    private final CompletedEventPublications completedEventPublications;
    private final Duration reintentoTras;
    private final Duration retencionCompletados;

    public EventPublicationMaintenanceScheduler(IncompleteEventPublications incompleteEventPublications,
                                                 CompletedEventPublications completedEventPublications,
                                                 @Value("${renaser.eventos.reintento-tras:PT5M}") Duration reintentoTras,
                                                 @Value("${renaser.eventos.retencion-completados:P7D}")
                                                 Duration retencionCompletados) {
        this.incompleteEventPublications = incompleteEventPublications;
        this.completedEventPublications = completedEventPublications;
        this.reintentoTras = reintentoTras;
        this.retencionCompletados = retencionCompletados;
    }

    /** Cada 5 minutos por defecto (configurable): reintenta publicaciones incompletas mas
     * viejas que {@link #reintentoTras} sin esperar a un restart del proceso. */
    @Scheduled(cron = "${renaser.eventos.mantenimiento-cron:0 */5 * * * *}", zone = "UTC")
    @SchedulerLock(name = "shared-reintentar-publicaciones-incompletas",
            lockAtMostFor = "${renaser.scheduling.shedlock.shared-reintentar-publicaciones.lock-at-most-for:PT4M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.shared-reintentar-publicaciones.lock-at-least-for:PT10S}")
    public void reintentarPublicacionesIncompletas() {
        log.debug("[shared.EventPublicationMaintenanceScheduler] reintentando publicaciones incompletas "
                + "mas viejas que {}", reintentoTras);
        incompleteEventPublications.resubmitIncompletePublicationsOlderThan(reintentoTras);
    }

    /** Higiene de la tabla (red de seguridad, ver javadoc de la clase): en el camino feliz
     * (completion-mode=DELETE) no deberia encontrar nada. Mismo horario que
     * `notifications.PurgaNotificacionesScheduler` para no competir con la ventana de mayor
     * trafico. */
    @Scheduled(cron = "0 45 4 * * *", zone = "UTC")
    @SchedulerLock(name = "shared-purgar-publicaciones-completadas",
            lockAtMostFor = "${renaser.scheduling.shedlock.shared-purgar-publicaciones.lock-at-most-for:PT30M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.shared-purgar-publicaciones.lock-at-least-for:PT1M}")
    public void purgarPublicacionesCompletadasAntiguas() {
        completedEventPublications.deletePublicationsOlderThan(retencionCompletados);
    }
}
