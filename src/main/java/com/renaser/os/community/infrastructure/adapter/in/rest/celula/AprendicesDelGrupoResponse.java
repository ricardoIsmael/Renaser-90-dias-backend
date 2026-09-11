package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.acompanamiento.ConsultarAprendicesDelGrupoUseCase.AprendizDelGrupo;
import com.renaser.os.community.application.ports.in.acompanamiento.ConsultarAprendicesDelGrupoUseCase.PaginaAprendices;

import java.util.List;
import java.util.UUID;

/** Respuesta de {@code GET /api/v1/mentor/groups/{groupId}/learners}. */
public record AprendicesDelGrupoResponse(UUID groupId, String groupName, String coverage, int total,
                                          List<Learner> learners, UUID nextCursor) {

    public static AprendicesDelGrupoResponse from(PaginaAprendices pagina) {
        return new AprendicesDelGrupoResponse(pagina.grupoId(), pagina.grupoNombre(), pagina.cobertura(),
                pagina.total(), pagina.aprendices().stream().map(Learner::from).toList(),
                pagina.siguienteCursor());
    }

    /** Solo identidad. El avance de cada uno se pide aparte, no en la lista. */
    public record Learner(UUID userId, String fullName, String avatarUrl, boolean active) {

        static Learner from(AprendizDelGrupo aprendiz) {
            return new Learner(aprendiz.participanteId(), aprendiz.nombre(), aprendiz.avatarUrl(),
                    aprendiz.activo());
        }
    }
}
