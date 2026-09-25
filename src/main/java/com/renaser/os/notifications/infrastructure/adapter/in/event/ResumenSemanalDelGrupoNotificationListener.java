package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.mentoring.api.ResumenSemanalDelGrupoEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.notifications.domain.model.semaforo.AvisoRedactado;
import com.renaser.os.notifications.domain.model.semaforo.ConteoDeLaSemana;
import com.renaser.os.notifications.domain.model.semaforo.RedaccionDelSemaforo;
import com.renaser.os.shared.domain.UserId;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Le avisa al mentor que su grupo cerró la semana del semáforo (D-168,
 * docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §1.2): bandeja y push, tipo {@code RESUMEN_SEMANAL}.
 *
 * <p><b>El texto nombra al grupo y a nadie más, y no dice cuántos quedaron en cada color</b>: el
 * conteo solo elige el caso ({@link RedaccionDelSemaforo}). Sale
 * igual por push, que no lleva métricas; el detalle —con nombres— se ve en la tabla del grupo, contra
 * un endpoint que revalida que ese mentor lo siga acompañando (si rotó entre el aviso y el toque, no
 * ve nada).
 *
 * <p>La deduplicación viaja en {@code claveDeduplicacion} (una por grupo y semana), igual que en
 * {@link AvisoAcompanamientoNotificationListener}: las corridas repetidas del resumen no crean otra.
 */
@Component
class ResumenSemanalDelGrupoNotificationListener {

    private final EmitirNotificacionUseCase emitirNotificacionUseCase;

    ResumenSemanalDelGrupoNotificationListener(EmitirNotificacionUseCase emitirNotificacionUseCase) {
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
    }

    @ApplicationModuleListener
    void on(ResumenSemanalDelGrupoEvent event) {
        AvisoRedactado aviso = RedaccionDelSemaforo.paraElMentor(event.grupoNombre(),
                new ConteoDeLaSemana(event.verde(), event.amarillo(), event.rojo(), event.sinDatos()));
        emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(
                UserId.of(event.mentorId()), TipoNotificacion.RESUMEN_SEMANAL, aviso.titulo(), aviso.cuerpo(),
                event.rutaApp(), event.claveDeduplicacion()));
    }
}
