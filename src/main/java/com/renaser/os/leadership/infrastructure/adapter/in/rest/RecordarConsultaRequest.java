package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/leadership/tickets/{ticketId}/recordar}.
 *
 * @param operationKey la misma en un reintento: el recordatorio sale una sola vez
 */
public record RecordarConsultaRequest(@NotBlank @Size(max = 100) String operationKey) {
}
