package com.renaser.os.rag.infrastructure.adapter.in.event;

import com.renaser.os.habits.api.AvisoHabitoDebidoEvent;
import com.renaser.os.rag.application.ports.in.aviso.DejarAvisoHabitoEnChatUseCase;
import com.renaser.os.rag.application.ports.in.aviso.DejarAvisoHabitoEnChatUseCase.AvisoHabitoEnChatCommand;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Segundo consumidor de {@code habits.api.AvisoHabitoDebidoEvent}, al lado de
 * {@code notifications.AvisoHabitoNotificationListener}: aquel manda el push, este deja el mensaje
 * del acompanante en el chat (fase 5, §5.1 de la propuesta del acompanante). Son independientes:
 * cada {@code @ApplicationModuleListener} tiene su propia publicacion en el outbox, asi que un
 * fallo aca no frena el push ni al reves.
 *
 * <p>Solo traduce el evento al comando del puerto de entrada; si el aviso pasa al chat lo decide
 * el caso de uso (interruptor apagado por defecto).
 */
@Component
class AvisoHabitoEnChatListener {

    private final DejarAvisoHabitoEnChatUseCase dejarAvisoHabitoEnChatUseCase;

    AvisoHabitoEnChatListener(DejarAvisoHabitoEnChatUseCase dejarAvisoHabitoEnChatUseCase) {
        this.dejarAvisoHabitoEnChatUseCase = dejarAvisoHabitoEnChatUseCase;
    }

    @ApplicationModuleListener
    void on(AvisoHabitoDebidoEvent event) {
        dejarAvisoHabitoEnChatUseCase.dejarEnElChat(new AvisoHabitoEnChatCommand(event.participanteId(),
                event.tituloHabito(), event.tipoAviso(), event.minutosQueFaltan(), event.puntosEnJuego(),
                event.claveEvento(), event.occurredAt()));
    }
}
