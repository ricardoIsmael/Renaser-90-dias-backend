package com.renaser.os.users.infrastructure.adapter.in.rest.participante;

import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;

import java.util.UUID;

/**
 * Forma cercana a {@code ProgramActivationData} del cliente movil real
 * (services/onboarding.ts) — sin {@code minStartDate}/{@code maxStartDate} (ya cerrada
 * la ventana tras activar, ver {@code EstadoActivacionProgramaResponse} para
 * consultarla ANTES de activar) ni {@code activated} (este endpoint solo responde 200
 * cuando la activacion ocurrio; si ya estaba activado devuelve 409, no un 200 con
 * {@code activated:false} — diferencia de contrato deliberada, ver reporte de D-66).
 */
public record ActivarProgramaResponse(UUID traineeProfileId, int programDay, String programActivatedAt,
                                       String startDate, String expectedGraduationDate) {

    public static ActivarProgramaResponse from(ParticipacionPrograma p) {
        return new ActivarProgramaResponse(p.participanteId().value(), p.diaPrograma(),
                p.programaActivadoEn().toString(), p.fechaInicio().toString(),
                p.fechaGraduacionEsperada().toString());
    }
}
