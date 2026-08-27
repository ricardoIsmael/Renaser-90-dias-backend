package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarCandidatosCelulaUseCase.AprendizCandidato;

/** Sin {@code programDay}/{@code currentPhase}/{@code coherenceScore}: esos campos viven
 * en un {@code TraineeProfile} que `users` todavia no construyo como dominio propio
 * (gap #1, docs/PLAN_INTEGRACION_FRONTEND.md sec. 5) ni en `points` (coherenceScore) —
 * no se inventan valores default para rellenarlos. */
public record AprendizCandidatoResponse(String userId, String fullName, String avatarUrl) {

    public static AprendizCandidatoResponse from(AprendizCandidato candidato) {
        return new AprendizCandidatoResponse(candidato.userId().toString(), candidato.nombreCompleto(),
                candidato.avatarUrl());
    }
}
