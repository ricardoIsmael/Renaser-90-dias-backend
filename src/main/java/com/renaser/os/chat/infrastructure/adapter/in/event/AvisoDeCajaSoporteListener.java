package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.caja.AvisarCajaEnSoporteUseCase;
import com.renaser.os.onboarding.api.AvisoDeCajaEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Lleva los avisos de la Caja Renaser (D-219) al chat de soporte del aprendiz. {@code @ApplicationModuleListener}:
 * después del commit del paso, en su propia transacción, y el outbox lo reentrega si falla. Los mensajes van
 * todos en esa transacción: o salen todos, o ninguno y se reintenta.
 */
@Component
class AvisoDeCajaSoporteListener {

    private final AvisarCajaEnSoporteUseCase avisar;

    AvisoDeCajaSoporteListener(AvisarCajaEnSoporteUseCase avisar) {
        this.avisar = avisar;
    }

    @ApplicationModuleListener
    void on(AvisoDeCajaEvent aviso) {
        avisar.avisar(aviso);
    }
}
