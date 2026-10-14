package com.renaser.os.rag.infrastructure.adapter.in.event;

import com.renaser.os.habits.api.AvisoHabitoDebidoEvent;
import com.renaser.os.rag.application.ports.in.aviso.DejarAvisoHabitoEnChatUseCase;
import com.renaser.os.rag.application.ports.in.aviso.DejarAvisoHabitoEnChatUseCase.AvisoHabitoEnChatCommand;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.application.ProcesosConInterruptor;
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
    private final Interruptores interruptores;

    AvisoHabitoEnChatListener(DejarAvisoHabitoEnChatUseCase dejarAvisoHabitoEnChatUseCase,
                              Interruptores interruptores) {
        this.dejarAvisoHabitoEnChatUseCase = dejarAvisoHabitoEnChatUseCase;
        this.interruptores = interruptores;
    }

    @ApplicationModuleListener
    void on(AvisoHabitoDebidoEvent event) {
        // Spec 002 (D-267): con SER («ia») apagada, los mensajes automaticos de SER saltan (politica del catalogo).
        if (ProcesosConInterruptor.saltaEsteTurno(interruptores, "ia", "rag.aviso-habito-en-chat")) {
            return;
        }
        dejarAvisoHabitoEnChatUseCase.dejarEnElChat(new AvisoHabitoEnChatCommand(event.participanteId(),
                event.tituloHabito(), event.tipoAviso(), event.minutosQueFaltan(), event.puntosEnJuego(),
                event.claveEvento(), event.occurredAt()));
    }
}
