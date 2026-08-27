package com.renaser.os.community.api;

import com.renaser.os.shared.domain.UserId;

import java.util.Optional;
import java.util.UUID;

/**
 * Contrato publico de `community` sobre las celulas (D-41: ningun modulo consulta la
 * tabla de otro de frente). `celulas` es de `community`; otros modulos que necesiten
 * saber quien lidera una celula pasan por aca.
 *
 * <p>Su primer consumidor es `calendar`: para un evento de audiencia CELULA, los
 * destinatarios son los participantes activos de la celula (eso lo da
 * `users.api.ParticipacionProgramaFinder`) MAS el mentor que la lidera, que no tiene
 * fila en `participantes_programa` por ser mentor y se perderia sin esto.
 */
public interface CelulaFinder {

    /** Mentor que lidera la celula, vacio si no existe la celula o no tiene mentor asignado. */
    Optional<UserId> mentorDe(UUID celulaId);

    /**
     * La celula del participante, con lo minimo para mostrarla fuera de `community` — hoy
     * su unico consumidor es el agregador `GET /api/v1/ranking` de `points` (gap #24,
     * docs/PLAN_INTEGRACION_FRONTEND.md §3/§5). Vacio si el participante todavia no tiene
     * celula asignada (estado normal del proceso, no un error — mismo criterio que
     * {@code ConsultarMiCelulaUseCase.miCelula}).
     *
     * <p>Deliberadamente NO incluye {@code coherenceScoreGroup}/{@code rankingPosition} de
     * la celula dentro de su cohorte: esa tabla (`ranking_celulas`) y su formula de calculo
     * son una pregunta abierta de arquitectura (docs/MODULO_POINTS.md Q-1/Q-1b — que modulo
     * la genera) que este cambio no resuelve por su cuenta.
     */
    Optional<CelulaParticipanteResumen> celulaDeParticipante(UserId participanteId);

    /** Proyeccion publica de la celula de un participante, sin exponer los tipos de dominio de `community`. */
    record CelulaParticipanteResumen(UUID celulaId, String cellName, String cohortName, String mentorName,
                                      int memberCount, int totalCellsInCohort) {
    }
}
