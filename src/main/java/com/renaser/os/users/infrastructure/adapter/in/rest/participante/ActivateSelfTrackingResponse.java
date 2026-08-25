package com.renaser.os.users.infrastructure.adapter.in.rest.participante;

import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;

import java.util.UUID;

/**
 * Espejo literal de {@code ActivateSelfTrackingResponse} del backend viejo
 * (src/features/mentor/service.ts: {@code {traineeProfileId, programDay}}). La app
 * movil hoy solo mira {@code response.ok}/409 (ver {@code ActivateSelfTrackingResult}
 * en mentorService.ts), pero el shape se conserva por si algun consumidor futuro lo lee.
 */
public record ActivateSelfTrackingResponse(UUID traineeProfileId, int programDay) {

    public static ActivateSelfTrackingResponse from(ParticipacionPrograma participacion) {
        return new ActivateSelfTrackingResponse(participacion.participanteId().value(), participacion.diaPrograma());
    }
}
