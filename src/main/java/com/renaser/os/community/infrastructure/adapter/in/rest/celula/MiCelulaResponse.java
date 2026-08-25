package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarMiCelulaUseCase.MiCelula;
import com.renaser.os.community.domain.model.cohorte.EstadoCohorte;

/**
 * CM-01: GET /api/v1/me/cell. Sin {@code coherenceScoreGroup}/{@code rankingPosition} —
 * viven en `ranking_celulas`, tabla de `points` (CLAUDE.MD sec. 6, columnas ELIMINADAS de
 * `celulas` por P-18 en el baseline nuevo); un futuro agregador entre `community` y
 * `points` las suma sin tocar este endpoint.
 */
public record MiCelulaResponse(String cellId, String cellName, String cohortName, String cohortStatus,
                                String mentorName, String mentorAvatarUrl, int memberCount,
                                int totalCellsInCohort, String videoCallUrl, String nextSessionAt) {

    public static MiCelulaResponse from(MiCelula miCelula) {
        return new MiCelulaResponse(miCelula.celula().id().toString(), miCelula.celula().nombre(),
                miCelula.cohorte().nombre(), toWireEstado(miCelula.cohorte().estado()),
                miCelula.mentor() != null ? miCelula.mentor().nombreCompleto() : null,
                miCelula.mentor() != null ? miCelula.mentor().avatarUrl() : null, miCelula.cantidadMiembros(),
                miCelula.totalCelulasEnCohorte(), miCelula.celula().urlVideollamada(),
                miCelula.celula().proximaSesionEn() != null ? miCelula.celula().proximaSesionEn().toString() : null);
    }

    private static String toWireEstado(EstadoCohorte estado) {
        return switch (estado) {
            case PLANIFICADA -> "PLANNED";
            case ACTIVA -> "ACTIVE";
            case COMPLETADA -> "COMPLETED";
        };
    }
}
