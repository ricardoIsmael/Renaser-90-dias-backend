package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.leadership.api.RevisionDelLiderEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * «Tu líder revisó a tu grupo»: le cuenta al mentor, una vez al día, qué personas de sus grupos miró el líder de
 * mentores (spec 005, FR-017). Bandeja y push, tipo {@code REVISION_DEL_LIDER}, propio para que se pueda silenciar sin
 * silenciar otra cosa; {@code EmitirNotificacionUseCase} respeta las preferencias del mentor (FR-018).
 *
 * <p>El cuerpo llega ya redactado por {@code leadership} y solo nombra a las personas por su primer nombre. Tocar el aviso
 * abre «Mi grupo» del grupo con más personas miradas; la app revalida allí que el mentor siga acompañando ese grupo.
 *
 * <p>La deduplicación viaja en {@code claveDeduplicacion} (una por mentor y día local): repetir la corrida horaria no crea
 * otro aviso ni otro push.
 */
@Component
class RevisionDelLiderNotificationListener {

    static final String TITULO = "Tu líder revisó a tu grupo";

    private final EmitirNotificacionUseCase emitirNotificacionUseCase;

    RevisionDelLiderNotificationListener(EmitirNotificacionUseCase emitirNotificacionUseCase) {
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
    }

    @ApplicationModuleListener
    void on(RevisionDelLiderEvent event) {
        emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(UserId.of(event.mentorId()),
                TipoNotificacion.REVISION_DEL_LIDER, TITULO, event.cuerpo(), event.rutaApp(),
                event.claveDeduplicacion()));
    }
}
