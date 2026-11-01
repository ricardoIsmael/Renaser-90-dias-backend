package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.analisis.GenerarAnalisis8020Port;
import com.renaser.os.rag.domain.model.analisis.TextoDelAnalisis;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Sin proveedor de IA configurado (desarrollo, pruebas): el análisis no se genera y no se cuentan intentos; las cifras se ven igual
 * (SC-004). Mismo criterio que {@code NoOpRenasiaChatAdapter}: se activa con {@code renaser.ia.proveedor=noop} o sin la propiedad.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.proveedor", havingValue = "noop", matchIfMissing = true)
class NoOpAnalisis8020Adapter implements GenerarAnalisis8020Port {

    @Override
    public boolean disponible() {
        return false;
    }

    @Override
    public TextoDelAnalisis generar(PedidoDeAnalisis pedido) {
        throw new IllegalStateException("No hay proveedor de IA configurado: no se genera el análisis");
    }
}
