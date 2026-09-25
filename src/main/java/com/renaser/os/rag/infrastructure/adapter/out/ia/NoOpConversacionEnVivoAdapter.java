package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.ia.ConversacionEnVivoPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * El default ({@code renaser.ia.voz.en-vivo.activa=false}): no hay voz en vivo. El caso de uso le
 * responde a la app "no disponible" y la app usa el flujo anterior (reconocimiento de voz del
 * telefono, chat y voz Kore). Igual que el resto de los NoOp de IA.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.voz.en-vivo.activa", havingValue = "false", matchIfMissing = true)
class NoOpConversacionEnVivoAdapter implements ConversacionEnVivoPort {

    @Override
    public boolean disponible() {
        return false;
    }

    @Override
    public SesionEnVivo abrir(Apertura apertura, Oyente oyente) {
        throw new ConversacionEnVivoNoDisponibleException("la voz en vivo esta apagada (renaser.ia.voz.en-vivo.activa)");
    }
}
