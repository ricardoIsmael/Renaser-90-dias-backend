package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.ia.SintetizarVozPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * El default ({@code renaser.ia.voz.proveedor=noop}): no hay voz del servidor, el endpoint
 * responde 204 y la app habla con el TTS del telefono. Asi la app arranca y funciona sin el
 * servicio de voz levantado, igual que con el resto de los NoOp de IA.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.voz.proveedor", havingValue = "noop", matchIfMissing = true)
class NoOpVozAdapter implements SintetizarVozPort {

    @Override
    public Optional<byte[]> sintetizar(String texto) {
        return Optional.empty();
    }
}
