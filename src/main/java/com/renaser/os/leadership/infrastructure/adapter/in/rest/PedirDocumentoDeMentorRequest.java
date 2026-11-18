package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import java.time.LocalDate;

/**
 * {@code POST /api/v1/leadership/mentors/{mentorId}/documento}.
 *
 * @param semanaHasta el viernes que cierra la semana; opcional: sin él, la semana en curso
 */
public record PedirDocumentoDeMentorRequest(LocalDate semanaHasta) {
}
