package com.renaser.os.onboarding.infrastructure.adapter.in.rest.grabacionv90;

import com.renaser.os.onboarding.domain.model.grabacionv90.EstadoIAv90;
import com.renaser.os.onboarding.domain.model.grabacionv90.GrabacionV90;

import java.math.BigDecimal;
import java.time.Instant;

public record GrabacionV90Response(Long id, String phase, String axis, short index, String questionKey,
                                    boolean recorded, Long mediaId, BigDecimal durationSeconds,
                                    EstadoIAv90 validationStatus, short validationAttempts, Instant recordedAt) {

    public static GrabacionV90Response from(GrabacionV90 g) {
        return new GrabacionV90Response(g.id(), g.fase(), g.eje(), g.indice(), g.clavePregunta(), g.grabada(),
                g.mediaId(), g.duracionSegundos(), g.estadoIa(), g.intentosIa(), g.grabadaEn());
    }
}
