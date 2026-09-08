package com.renaser.os.onboarding.application.ports.out.mapa;

import com.renaser.os.onboarding.domain.model.mapa.AccionesDelMapa;
import com.renaser.os.onboarding.domain.model.mapa.ProtocolosDelMapa;
import com.renaser.os.shared.domain.UserId;

/** Lectura de las dos listas del Mapa que no entran en `respuestas_onboarding` (V41). */
public interface LoadMapaPort {

    AccionesDelMapa accionesDe(UserId participanteId);

    ProtocolosDelMapa protocolosDe(UserId participanteId);
}
