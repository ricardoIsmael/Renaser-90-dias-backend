package com.renaser.os.habits.application.ports.in.radar;

import com.renaser.os.habits.domain.model.radar.PlanDeMiRadar;
import com.renaser.os.shared.domain.UserId;

/** Mi Radar de la propia persona (spec 019): si esta encendido y que pregunta cada dia de la semana. */
public interface ConsultarMiRadarUseCase {

    /** Autoservicio: actorId debe ser el propio participanteId. */
    PlanDeMiRadar consultar(UserId actorId, UserId participanteId);
}
