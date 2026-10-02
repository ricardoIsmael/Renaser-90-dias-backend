package com.renaser.os.users.application.ports.in.emergencia;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.emergencia.SolicitudDeEmergencia;

import java.util.Optional;
import java.util.UUID;

/**
 * Quien atiende soporte (ADMIN/ALCHEMIST, los mismos que están en cada chat de soporte) ve el pedido abierto
 * de una persona y lo cierra sin cambiar el día. Cambiándolo se cierra solo
 * ({@link ResolverEmergenciaAlCambiarDiaUseCase}).
 */
public interface AtenderEmergenciaUseCase {

    /** El pedido abierto de esa persona, con su nombre y el día que vive hoy; vacío si no tiene. */
    Optional<EmergenciaParaSoporte> abiertaDe(UserId actorId, UserId aprendizId);

    /**
     * @throws java.util.NoSuchElementException si el pedido no existe (404)
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si quien cierra no es ADMIN/ALCHEMIST activo (403)
     * @throws IllegalStateException si ya estaba resuelto (409)
     */
    SolicitudDeEmergencia cerrarSinCambio(UserId actorId, UUID solicitudId);

    /** @param diaActual el día que vive hoy (el reloj siguió corriendo desde que lo pidió) */
    record EmergenciaParaSoporte(SolicitudDeEmergencia solicitud, String nombre, int diaActual) {
    }
}
