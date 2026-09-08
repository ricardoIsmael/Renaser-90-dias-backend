package com.renaser.os.onboarding.application.ports.in.mapa;

import com.renaser.os.onboarding.domain.model.mapa.AccionesDelMapa;
import com.renaser.os.onboarding.domain.model.mapa.ProtocolosDelMapa;
import com.renaser.os.shared.domain.UserId;

/**
 * Lo que el motor de cuestionarios NO puede devolver del Mapa: las dos listas y si la etapa ya se
 * dio por terminada. El resto —los tres objetivos, los nueve hitos, la prioridad, el retorno y el
 * compromiso— ya sale de `GET /api/v1/onboarding/answers?flow=mapa_dia7`, que existe desde antes.
 */
public interface ConsultarMapaUseCase {

    MapaDelParticipante consultar(UserId actorId);

    record MapaDelParticipante(AccionesDelMapa acciones, ProtocolosDelMapa protocolos, boolean etapaCompletada) {
    }
}
