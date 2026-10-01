package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.ConsultarPadronDeMentoresUseCase.PadronDeMentores;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /api/v1/leadership/mentors}: el padrón. {@code month}, {@code timezone} y {@code cutoffAt}
 * dicen de cuándo es cada cifra (RL-20); {@code semaforoFrom/To}, la ventana del semáforo.
 */
public record MentorRosterResponse(String month, String timezone, Instant cutoffAt, LocalDate semaforoFrom,
                                   LocalDate semaforoTo, List<MentorIndicatorsResponse> mentors) {

    static MentorRosterResponse from(PadronDeMentores padron) {
        return new MentorRosterResponse(padron.mes(), padron.zona(), padron.corteEn(), padron.semaforoDesde(),
                padron.semaforoHasta(),
                padron.mentores().stream().map(m -> MentorIndicatorsResponse.from(m, true)).toList());
    }
}
