package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.conversacion.DarBienvenidaEnGrupoUseCase;
import com.renaser.os.community.api.ComposicionDeCelulaCambiadaEvent;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.application.ProcesosConInterruptor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Da la bienvenida en el chat del grupo cuando cambia la composición (D-191; la firma el programa
 * desde D-204): entra un aprendiz, o el grupo recibe mentor y tenía aprendices esperando.
 *
 * <p>Un listener propio, igual que {@code ComposicionCelulaAcompananteListener}: cada
 * {@code @ApplicationModuleListener} corre después del commit, en otro hilo y en su transacción, así
 * que un fallo acá no frena la sincronización del chat ni los chats de dos, y al revés. La
 * reentrega del outbox no duplica: la marca es por pertenencia.
 */
@Component
class ComposicionCelulaBienvenidaListener {

    private final DarBienvenidaEnGrupoUseCase darBienvenidas;
    private final Interruptores interruptores;

    ComposicionCelulaBienvenidaListener(DarBienvenidaEnGrupoUseCase darBienvenidas, Interruptores interruptores) {
        this.interruptores = interruptores;
        this.darBienvenidas = darBienvenidas;
    }

    @ApplicationModuleListener
    void on(ComposicionDeCelulaCambiadaEvent event) {
        // Spec 002: con la bienvenida apagada, salta; con el chat apagado, se pospone (el outbox la reentrega).
        if (ProcesosConInterruptor.saltaEsteTurno(interruptores, "admin.bienvenida", "chat.bienvenida-en-grupo")) {
            return;
        }
        ProcesosConInterruptor.posponerSiEstaApagada(interruptores, "chat", "chat.bienvenida-en-grupo");
        darBienvenidas.darBienvenidas(event.celulaId());
    }
}
