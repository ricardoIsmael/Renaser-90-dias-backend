package com.renaser.os.habits.application.ports.in.radar;

import com.renaser.os.habits.domain.model.radar.MiRadar;
import com.renaser.os.shared.domain.UserId;

/** Mi Radar de la propia persona (spec 012): si esta encendido y a que horas. */
public interface ConsultarMiRadarUseCase {

    /** Autoservicio: actorId debe ser el propio participanteId. */
    MiRadar consultar(UserId actorId, UserId participanteId);
}
