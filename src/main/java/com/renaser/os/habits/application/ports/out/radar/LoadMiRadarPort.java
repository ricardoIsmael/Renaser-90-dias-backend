package com.renaser.os.habits.application.ports.out.radar;

import com.renaser.os.habits.domain.model.radar.MiRadar;
import com.renaser.os.shared.domain.UserId;

public interface LoadMiRadarPort {

    /** Siempre devuelve algo: sin nada guardado es {@link MiRadar#apagado()}. */
    MiRadar deParticipante(UserId participanteId);

    /** La hora lógica del último guardado de Mi Radar (spec 048, D13); vacío si nunca se tocó: se lee como «muy viejo». */
    java.util.Optional<java.time.Instant> actualizadoEn(UserId participanteId);
}
