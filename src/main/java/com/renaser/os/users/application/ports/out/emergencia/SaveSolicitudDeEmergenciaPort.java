package com.renaser.os.users.application.ports.out.emergencia;

import com.renaser.os.users.domain.model.emergencia.SolicitudDeEmergencia;

/** Alta y cierre de un pedido de emergencia (V91). Nunca borra. */
public interface SaveSolicitudDeEmergenciaPort {

    SolicitudDeEmergencia save(SolicitudDeEmergencia solicitud);
}
