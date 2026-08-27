package com.renaser.os.users.infrastructure.adapter.in.rest.participante;

import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;

/** Forma exacta de {@code UpdateTraineeProfileResponse} en el frontend real (services/profile.ts). */
public record TraineeProfileResponse(String id, String personalChallengeName, String goalType, String timezone,
                                      String updatedAt) {

    public static TraineeProfileResponse from(ParticipacionPrograma p) {
        return new TraineeProfileResponse(p.participanteId().value().toString(), p.nombreRetoPersonal(),
                p.tipoMeta() == null ? null : p.tipoMeta().name(), p.timezone().getId(),
                p.actualizadoEn().toString());
    }
}
