package com.renaser.os.habits.application.ports.out.radar;

import com.renaser.os.habits.domain.model.radar.MiRadar;
import com.renaser.os.shared.domain.UserId;

public interface SaveMiRadarPort {

    /** Reemplaza lo guardado: encendido y los dias que no son de fabrica. */
    void guardar(UserId participanteId, MiRadar miRadar);
}
