package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.calendar.api.RecordatorioEventoDebidoEvent;
import com.renaser.os.notifications.application.ports.in.evento.RecordarEventoEnElChatUseCase;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Lleva el recordatorio de un evento al chat de soporte de la persona (spec 041, FR-001 a FR-006). Un listener propio, aparte del que entrega el aviso de la app y
 * del de WhatsApp: cada uno tiene su propia publicacion en el *outbox*, asi que un fallo del chat no repite el WhatsApp ni el aviso del telefono, y viceversa.
 * El mensaje se deduplica por el id de la fila de la cola ({@code chat} lo calcula de ahi): la reentrega no lo duplica ni lo pierde.
 */
@Component
class RecordatorioEventoEnElChatListener {

    private final RecordarEventoEnElChatUseCase recordar;

    RecordatorioEventoEnElChatListener(RecordarEventoEnElChatUseCase recordar) {
        this.recordar = recordar;
    }

    @ApplicationModuleListener
    void on(RecordatorioEventoDebidoEvent event) {
        if (event.esAvisoDeMensaje()) {
            recordar.recordar(AvisoDeMensajeDelEventoMapper.de(event));
        }
    }
}
