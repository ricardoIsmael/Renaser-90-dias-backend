package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarCandidatosCelulaUseCase.MentorCandidato;

/**
 * {@code cellId} null si el mentor todavia no lidera ninguna celula (candidato para
 * "mentores-disponibles"; en "mentores" viaja para marcar a quien ya esta ocupado).
 *
 * <p>{@code specialty} entra con el SDD 003 y es NEGOCIO, MENTE, RELACIONES o {@code null}. Null
 * significa que el mentor no la declaro —asi quedaron los perfiles anteriores a V48— y la pantalla
 * lo muestra "Sin especialidad definida". No se rellena con ninguna de las tres: el administrador
 * elige el mentor del grupo justamente por esto, y adivinarla seria decidir por el.
 */
public record MentorCandidatoResponse(String userId, String fullName, String avatarUrl, String cellId,
                                       String specialty) {

    public static MentorCandidatoResponse from(MentorCandidato candidato) {
        return new MentorCandidatoResponse(candidato.userId().toString(), candidato.nombreCompleto(),
                candidato.avatarUrl(), candidato.celulaActual() != null ? candidato.celulaActual().toString() : null,
                candidato.especialidad() != null ? candidato.especialidad().name() : null);
    }
}
