package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.conversacion.DarBienvenidaEnGrupoUseCase;
import com.renaser.os.community.api.ComposicionDeCelulaCambiadaEvent;
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

    ComposicionCelulaBienvenidaListener(DarBienvenidaEnGrupoUseCase darBienvenidas) {
        this.darBienvenidas = darBienvenidas;
    }

    @ApplicationModuleListener
    void on(ComposicionDeCelulaCambiadaEvent event) {
        darBienvenidas.darBienvenidas(event.celulaId());
    }
}
