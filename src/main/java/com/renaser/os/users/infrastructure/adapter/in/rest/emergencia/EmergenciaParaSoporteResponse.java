package com.renaser.os.users.infrastructure.adapter.in.rest.emergencia;

import com.renaser.os.users.application.ports.in.emergencia.AtenderEmergenciaUseCase.EmergenciaParaSoporte;

/**
 * El pedido abierto de una persona para quien atiende soporte: lo que hace falta para abrir «Cambiar día del
 * programa» con el día pedido ya puesto.
 *
 * @param diaPedido {@code null} si lo pidió en el Día 0 (solo «necesito ayuda»): no hay día que aplicar
 * @param diaActual el día que vive HOY (el reloj siguió corriendo desde {@code diaAlPedir})
 */
public record EmergenciaParaSoporteResponse(String id, String aprendizId, String nombre, String queOcurrio,
                                            Integer diaPedido, int diaAlPedir, int diaActual, String creadaEn) {

    static EmergenciaParaSoporteResponse from(EmergenciaParaSoporte e) {
        var s = e.solicitud();
        return new EmergenciaParaSoporteResponse(s.id().toString(), s.aprendizId().value().toString(), e.nombre(),
                s.queOcurrio(), s.diaPedido(), s.diaAlPedir(), e.diaActual(), s.creadaEn().toString());
    }
}
