package com.renaser.os.users.infrastructure.adapter.in.rest.admin;

import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.participante.ListTraineesUseCase.ResumenTraineeAdmin;

import java.util.UUID;

/** Fila del listado del panel admin de aprendices (gap #7). */
public record TraineeSummaryResponse(String id, String fullName, String email, UserStatus status, int programDay,
                                      FasePrograma phase, UUID cellId, String mentorId) {

    public static TraineeSummaryResponse from(ResumenTraineeAdmin resumen) {
        return new TraineeSummaryResponse(resumen.id().toString(), resumen.fullName(), resumen.email(),
                resumen.status(), resumen.diaPrograma(), resumen.fase(), resumen.celulaId(),
                resumen.mentorId() == null ? null : resumen.mentorId().toString());
    }
}
