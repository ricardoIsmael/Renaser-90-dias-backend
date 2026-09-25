package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.memoria.CompactarConversacionPort;
import com.renaser.os.rag.domain.model.memoria.Compactacion;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Sin proveedor de IA no se compacta: no devuelve ninguna categoria ni resumen, y eso conserva lo que
 * ya habia ({@code Compactacion#textosQueQuedan}). Nunca borra la memoria de nadie (D-167).
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.proveedor", havingValue = "noop", matchIfMissing = true)
class NoOpCompactarConversacionAdapter implements CompactarConversacionPort {

    @Override
    public Compactacion compactar(Entrada entrada) {
        return new Compactacion("", Map.of());
    }
}
