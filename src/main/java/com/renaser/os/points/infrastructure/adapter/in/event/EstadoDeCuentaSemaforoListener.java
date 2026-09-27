package com.renaser.os.points.infrastructure.adapter.in.event;

import com.renaser.os.points.application.ports.in.semaforo.RegistrarSuspensionDelSemaforoUseCase;
import com.renaser.os.users.api.EstadoDeCuentaCambiadoEvent;
import com.renaser.os.users.api.UserStatus;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Los días con la cuenta suspendida no se miden (D-209): el semáforo se entera del cambio de estado por
 * el evento que ya publica {@code users} ({@code StaffAdminService.updateStatus}, el único camino que
 * suspende o reactiva una cuenta), sin leer su tabla (D-41).
 *
 * <p>Corre después del commit y en otro hilo (outbox de Modulith): la suspensión de la cuenta no espera
 * al semáforo, y si esto falla se reintenta solo. Por eso el caso de uso es idempotente y tolera que un
 * reintento llegue fuera de orden.
 */
@Component
class EstadoDeCuentaSemaforoListener {

    private final RegistrarSuspensionDelSemaforoUseCase suspensiones;

    EstadoDeCuentaSemaforoListener(RegistrarSuspensionDelSemaforoUseCase suspensiones) {
        this.suspensiones = suspensiones;
    }

    @ApplicationModuleListener
    void on(EstadoDeCuentaCambiadoEvent event) {
        if (event.estadoNuevo() == UserStatus.SUSPENDED) {
            suspensiones.alSuspender(event.usuarioId(), event.occurredAt());
        } else if (event.estadoAnterior() == UserStatus.SUSPENDED) {
            suspensiones.alReactivar(event.usuarioId(), event.occurredAt());
        }
    }
}
