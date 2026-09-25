package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.mentoring.api.ResumenSemanalDelGrupoEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Le avisa al mentor que su grupo cerró la semana del semáforo (D-168,
 * docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §1.2): bandeja y push, tipo {@code RESUMEN_SEMANAL}.
 *
 * <p><b>El texto nombra al grupo y a nadie más, y no dice cuántos quedaron en cada color.</b> Sale
 * igual por push, que no lleva métricas; el detalle —con nombres— se ve en la tabla del grupo, contra
 * un endpoint que revalida que ese mentor lo siga acompañando (si rotó entre el aviso y el toque, no
 * ve nada).
 *
 * <p>La deduplicación viaja en {@code claveDeduplicacion} (una por grupo y semana), igual que en
 * {@link AvisoAcompanamientoNotificationListener}: las corridas repetidas del resumen no crean otra.
 */
@Component
class ResumenSemanalDelGrupoNotificationListener {

    /* TEXTOS PROVISORIOS (D-168): los aprueba el dueño, igual que los del acompañante (D-155). El
       nombre va sin "grupo" delante porque muchos ya lo traen ("Grupo Amanecer"). */
    static final String TITULO = "Tu grupo cerró la semana";
    static final String CUERPO_SIN_NOMBRE = "El semáforo de tu grupo ya está listo.";

    private final EmitirNotificacionUseCase emitirNotificacionUseCase;

    ResumenSemanalDelGrupoNotificationListener(EmitirNotificacionUseCase emitirNotificacionUseCase) {
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
    }

    @ApplicationModuleListener
    void on(ResumenSemanalDelGrupoEvent event) {
        emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(
                UserId.of(event.mentorId()), TipoNotificacion.RESUMEN_SEMANAL, TITULO, cuerpo(event.grupoNombre()),
                event.rutaApp(), event.claveDeduplicacion()));
    }

    static String cuerpo(String grupoNombre) {
        return grupoNombre == null || grupoNombre.isBlank()
                ? CUERPO_SIN_NOMBRE
                : "El semáforo de " + grupoNombre.strip() + " ya está listo.";
    }
}
