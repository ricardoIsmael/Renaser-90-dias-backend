package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarCandidatosCelulaUseCase.MentorCandidato;

/** {@code cellId} null si el mentor todavia no lidera ninguna celula (candidato para
 * "mentores-disponibles"; en "mentores" viaja para marcar a quien ya esta ocupado). */
public record MentorCandidatoResponse(String userId, String fullName, String avatarUrl, String cellId) {

    public static MentorCandidatoResponse from(MentorCandidato candidato) {
        return new MentorCandidatoResponse(candidato.userId().toString(), candidato.nombreCompleto(),
                candidato.avatarUrl(), candidato.celulaActual() != null ? candidato.celulaActual().toString() : null);
    }
}
