package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.util.Objects;
import java.util.UUID;

/**
 * Quien atiende soporte resolvió el pedido de emergencia de un aprendiz (D-244): le cambió el día o lo cerró sin
 * cambiarlo. Se publica en la misma transacción que el cierre. Lo escucha {@code chat}, que le escribe a la
 * persona en su chat de soporte (pedido del dueño del 2026-10-02), una sola vez por pedido.
 *
 * @param diaAplicado el día al que se la llevó; {@code null} si se cerró sin cambiar el día
 * @param diaActual   el día que vive ahora (después del cambio, si lo hubo)
 */
public record EmergenciaResueltaEvent(UUID solicitudId, UserId aprendizId, Integer diaAplicado, int diaActual) {

    public EmergenciaResueltaEvent {
        Objects.requireNonNull(solicitudId, "solicitudId");
        Objects.requireNonNull(aprendizId, "aprendizId");
    }
}
