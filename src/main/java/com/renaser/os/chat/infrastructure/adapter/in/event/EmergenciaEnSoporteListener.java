package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.emergencia.AvisarEmergenciaEnSoporteUseCase;
import com.renaser.os.users.api.EmergenciaPedidaEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Lleva el pedido de emergencia (D-244) al chat de soporte del aprendiz. {@code @ApplicationModuleListener}:
 * después del commit del pedido, en su propia transacción, y el outbox lo reentrega si falla.
 */
@Component
class EmergenciaEnSoporteListener {

    private final AvisarEmergenciaEnSoporteUseCase avisar;

    EmergenciaEnSoporteListener(AvisarEmergenciaEnSoporteUseCase avisar) {
        this.avisar = avisar;
    }

    @ApplicationModuleListener
    void on(EmergenciaPedidaEvent pedido) {
        avisar.avisar(pedido);
    }
}
