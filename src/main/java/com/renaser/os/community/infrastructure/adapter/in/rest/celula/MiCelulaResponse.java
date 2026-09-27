package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarMiCelulaUseCase.MiCelula;
import com.renaser.os.community.domain.model.cohorte.EstadoCohorte;

/**
 * CM-01: GET /api/v1/me/cell. Sin {@code coherenceScoreGroup}/{@code rankingPosition} —
 * viven en `ranking_celulas`, tabla de `points` (CLAUDE.MD sec. 6, columnas ELIMINADAS de
 * `celulas` por P-18 en el baseline nuevo); un futuro agregador entre `community` y
 * `points` las suma sin tocar este endpoint.
 *
 * <p>{@code mentorId} y {@code mentorPhotoPath} (D-206/D-207, 2026-09-27): quién es el mentor —la app lo
 * compara con la sesión para decir «Tú» cuando el que mira es él, y lo usa para abrir el 1 a 1 con él
 * desde la info— y la ruta de su tarjeta con nombre en el chat del grupo, pedida con la sesión (va
 * {@code null} si el modo del servidor es {@code FOTO_SUBIDA} y el mentor subió foto: entonces se
 * muestra {@code mentorAvatarUrl}). {@code mentorPhotoPath} solo viene en {@code /me/cells}; en
 * {@code /me/cell} (el endpoint viejo) va {@code null}. Son campos nuevos: las versiones publicadas de la
 * app los ignoran (sus esquemas son {@code passthrough}).
 */
public record MiCelulaResponse(String cellId, String cellName, String cohortName, String cohortStatus,
                                String mentorName, String mentorAvatarUrl, int memberCount,
                                int totalCellsInCohort, String videoCallUrl, String nextSessionAt,
                                String mentorId, String mentorPhotoPath) {

    public static MiCelulaResponse from(MiCelula miCelula) {
        return new MiCelulaResponse(miCelula.celula().id().toString(), miCelula.celula().nombre(),
                miCelula.cohorte().nombre(), toWireEstado(miCelula.cohorte().estado()),
                miCelula.mentor() != null ? miCelula.mentor().nombreCompleto() : null,
                miCelula.mentor() != null ? miCelula.mentor().avatarUrl() : null, miCelula.cantidadMiembros(),
                miCelula.totalCelulasEnCohorte(), miCelula.celula().urlVideollamada(),
                miCelula.celula().proximaSesionEn() != null ? miCelula.celula().proximaSesionEn().toString() : null,
                miCelula.mentor() != null ? miCelula.mentor().id().toString() : null, miCelula.rutaFotoMentor());
    }

    private static String toWireEstado(EstadoCohorte estado) {
        return switch (estado) {
            case PLANIFICADA -> "PLANNED";
            case ACTIVA -> "ACTIVE";
            case COMPLETADA -> "COMPLETED";
        };
    }
}
