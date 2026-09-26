package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.conversacion.AbrirChatsConAcompananteUseCase;
import com.renaser.os.community.api.ComposicionDeCelulaCambiadaEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Abre los chats de dos cuando cambia quién está en un grupo (D-173): entra un aprendiz, el admin
 * le pone mentor, la recepción cambia de guías.
 *
 * <p>Separado de {@code ComposicionCelulaChatListener} a propósito: cada
 * {@code @ApplicationModuleListener} corre en su propia transacción, así que si abrir un chat de
 * dos falla, la lista de participantes del chat del grupo igual queda al día, y al revés.
 */
@Component
class ComposicionCelulaAcompananteListener {

    private final AbrirChatsConAcompananteUseCase abrirChats;

    ComposicionCelulaAcompananteListener(AbrirChatsConAcompananteUseCase abrirChats) {
        this.abrirChats = abrirChats;
    }

    @ApplicationModuleListener
    void on(ComposicionDeCelulaCambiadaEvent event) {
        abrirChats.abrirParaGrupo(event.celulaId());
    }
}
