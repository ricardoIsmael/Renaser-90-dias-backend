package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.ConsultarFichaDeMentorUseCase.FichaDeMentor;
import com.renaser.os.users.api.FichaDeMentorFinder.PerfilDeMentor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /api/v1/leadership/mentors/{id}}: la ficha de un mentor.
 *
 * @param profile null si todavía no tiene perfil de mentor
 */
public record MentorDetailResponse(MentorIndicatorsResponse mentor, boolean accountActive, ProfileResponse profile,
                                   String month, String timezone, Instant cutoffAt, LocalDate semaforoFrom,
                                   LocalDate semaforoTo, List<ObservationResponse> recentObservations) {

    static MentorDetailResponse from(FichaDeMentor ficha) {
        return new MentorDetailResponse(MentorIndicatorsResponse.from(ficha.indicadores(), true), ficha.cuentaActiva(),
                ProfileResponse.from(ficha.perfil()), ficha.mes(), ficha.zona(), ficha.corteEn(),
                ficha.semaforoDesde(), ficha.semaforoHasta(),
                ficha.ultimasObservaciones().stream().map(ObservationResponse::from).toList());
    }

    /**
     * @param level             N0..N3 (lo cambian ADMIN/ALCHEMIST)
     * @param operationalStatus GREEN/YELLOW/RED: el estado del MENTOR, distinto del semáforo de sus aprendices
     */
    public record ProfileResponse(String level, String operationalStatus, Instant since) {
        static ProfileResponse from(PerfilDeMentor perfil) {
            return perfil == null ? null : new ProfileResponse(perfil.nivel(), perfil.estadoOperativo(), perfil.desde());
        }
    }
}
