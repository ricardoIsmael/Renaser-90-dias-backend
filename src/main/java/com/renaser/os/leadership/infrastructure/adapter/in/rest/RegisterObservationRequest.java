package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code POST /api/v1/leadership/mentors/{id}/observations}.
 *
 * @param operationKey la misma en un reintento: no se crean dos observaciones
 * @param sentByChat   si el líder además se la mandó por el chat directo (la app ya envió el mensaje)
 * @param messageId    el mensaje enviado; null si no se mandó o si falló
 * @param personId     spec 005: la persona de la que trata («Avisar a su mentor» desde su ficha); opcional. Tiene que
 *                     estar hoy en un grupo de ese mentor, o es 400
 * @param weekStart    el primer día de la semana que el líder miraba; opcional y solo con {@code personId}
 */
public record RegisterObservationRequest(@NotBlank String type, @NotBlank @Size(max = 1000) String text,
                                         boolean sentByChat, UUID messageId,
                                         @NotBlank @Size(max = 100) String operationKey,
                                         UUID personId, LocalDate weekStart) {
}
