package com.renaser.os.habits.application.ports.out.radar;

import com.renaser.os.habits.domain.model.radar.RegistroRadar;

public interface SaveRegistroRadarPort {

    /** Append-only: no existe un `delete`/`update` de negocio para este puerto (sin edicion ni borrado). */
    RegistroRadar save(RegistroRadar registro);
}
