package com.renaser.os.users.infrastructure.adapter.in.rest.emergencia;

import com.renaser.os.users.application.ports.in.emergencia.PedirAyudaPorEmergenciaUseCase.MiEmergencia;

/**
 * @param diaMaximo hasta qué día puede pedir; 0 en el Día 0 (la app pide ayuda sin selector de día)
 * @param abierta   su pedido abierto, o {@code null}
 */
public record MiEmergenciaResponse(int diaActual, int diaMaximo, EmergenciaResponse abierta) {

    static MiEmergenciaResponse from(MiEmergencia mia) {
        return new MiEmergenciaResponse(mia.diaActual(), mia.diaMaximo(),
                mia.abierta() == null ? null : EmergenciaResponse.from(mia.abierta()));
    }
}
