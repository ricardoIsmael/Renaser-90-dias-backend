package com.renaser.os.habits.application.ports.out.radar;

import com.renaser.os.habits.domain.model.radar.MiRadar;
import com.renaser.os.shared.domain.UserId;

public interface LoadMiRadarPort {

    /** Siempre devuelve algo: sin horas guardadas es {@link MiRadar#apagado()}. */
    MiRadar deParticipante(UserId participanteId);
}
