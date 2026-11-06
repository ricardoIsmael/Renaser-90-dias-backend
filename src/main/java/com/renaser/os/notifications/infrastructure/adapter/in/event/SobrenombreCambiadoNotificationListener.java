package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.community.api.SobrenombreCambiadoPorAdministracionEvent;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Spec 046, D7 (06/11): si Administración cambia o quita el sobrenombre de una persona, se le avisa, sin decir el motivo (queda en la
 * constancia que ve Administración). Con {@code ANUNCIO_SISTEMA}, sin tipo nuevo.
 */
@Component
class SobrenombreCambiadoNotificationListener {

    static final String TITULO = "Tu sobrenombre en el Muro";
    static final String CAMBIADO = "Administración cambió tu sobrenombre en el Muro.";
    static final String QUITADO = "Administración quitó tu sobrenombre: en el Muro vuelves a verte con tu nombre.";

    private final EmitirNotificacionUseCase emitir;

    SobrenombreCambiadoNotificationListener(EmitirNotificacionUseCase emitir) {
        this.emitir = emitir;
    }

    @ApplicationModuleListener
    void on(SobrenombreCambiadoPorAdministracionEvent evento) {
        UUID origen = UUID.nameUUIDFromBytes(("sobrenombre:" + evento.persona() + ":" + evento.occurredAt()).getBytes(StandardCharsets.UTF_8));
        emitir.emitir(new EmitirNotificacionCommand(evento.persona(), TipoNotificacion.ANUNCIO_SISTEMA, TITULO,
                evento.quitado() ? QUITADO : CAMBIADO, null, origen));
    }
}
