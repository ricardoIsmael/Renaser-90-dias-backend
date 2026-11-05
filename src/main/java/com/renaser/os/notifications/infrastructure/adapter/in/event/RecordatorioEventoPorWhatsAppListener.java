package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.calendar.api.RecordatorioEventoDebidoEvent;
import com.renaser.os.notifications.application.ports.in.whatsapp.RecordarEventoPorWhatsAppUseCase;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Manda el recordatorio de un evento por WhatsApp (spec 041, FR-010 a FR-025). <b>No lanza por una falla del proveedor</b> (FR-021): el *outbox* reintentaria
 * y mandaria dos veces. Lanza solo si no pudo ni apartar la entrega, y entonces nada salio y el reintento es seguro. La llamada al proveedor la hace el caso de uso,
 * despues de apartar la entrega en su propia transaccion.
 */
@Component
class RecordatorioEventoPorWhatsAppListener {

    private final RecordarEventoPorWhatsAppUseCase recordar;

    RecordatorioEventoPorWhatsAppListener(RecordarEventoPorWhatsAppUseCase recordar) {
        this.recordar = recordar;
    }

    @ApplicationModuleListener
    void on(RecordatorioEventoDebidoEvent event) {
        if (event.esAvisoDeMensaje()) {
            recordar.recordar(AvisoDeMensajeDelEventoMapper.de(event));
        }
    }
}
