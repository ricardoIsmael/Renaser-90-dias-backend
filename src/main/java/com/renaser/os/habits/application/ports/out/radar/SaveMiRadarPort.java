package com.renaser.os.habits.application.ports.out.radar;

import com.renaser.os.habits.domain.model.radar.MiRadar;
import com.renaser.os.shared.domain.UserId;

public interface SaveMiRadarPort {

    /**
     * Reemplaza lo guardado: encendido y los dias que no son de fabrica, y deja su hora logica en {@code actualizadoEn} (spec 048:
     * la hora real si vino de la cola sin red). Es la hora con la que se decide un choque.
     */
    void guardar(UserId participanteId, MiRadar miRadar, java.time.Instant actualizadoEn);
}
