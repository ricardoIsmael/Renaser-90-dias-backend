package com.renaser.os.users.application.ports.in.emergencia;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.emergencia.SolicitudDeEmergencia;

import java.util.Objects;

/**
 * El botón «Tuve una emergencia» del aprendiz (D-244). Pedir NO cambia el día: guarda el pedido y avisa a
 * soporte (mensaje en su chat de soporte y aviso a quienes lo atienden).
 */
public interface PedirAyudaPorEmergenciaUseCase {

    /** Lo que la pantalla necesita para armarse: hasta qué día puede pedir y si ya tiene un pedido abierto. */
    MiEmergencia consultar(UserId actorId);

    /**
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si no es un aprendiz activo (403)
     * @throws IllegalArgumentException si el texto o el día no valen (400)
     * @throws IllegalStateException si ya tiene un pedido abierto (409)
     */
    SolicitudDeEmergencia pedir(PedirAyudaCommand command);

    /** @param diaPedido {@code null} en el Día 0: el pedido es solo «necesito ayuda». */
    record PedirAyudaCommand(UserId actorId, String queOcurrio, Integer diaPedido) {
        public PedirAyudaCommand {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
        }
    }

    /**
     * @param diaActual el día que vive hoy en su zona
     * @param diaMaximo hasta qué día puede pedir (0 en el Día 0: pide ayuda sin elegir día)
     * @param abierta   su pedido abierto, o {@code null}
     */
    record MiEmergencia(int diaActual, int diaMaximo, SolicitudDeEmergencia abierta) {
    }
}
