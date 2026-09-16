package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarCandidatosCelulaUseCase.AprendizCandidato;

/** Sin {@code programDay}/{@code currentPhase}/{@code coherenceScore}: esos campos viven
 * en un {@code TraineeProfile} que `users` todavia no construyo como dominio propio
 * (gap #1, docs/PLAN_INTEGRACION_FRONTEND.md sec. 5) ni en `points` (coherenceScore) —
 * no se inventan valores default para rellenarlos.
 *
 * <p>{@code cellId} null si el aprendiz no esta hoy en ningun grupo; con valor, es el grupo del
 * que la pantalla lo sacaria al elegirlo. <b>Mismo campo y mismo significado que en
 * {@link MentorCandidatoResponse}</b>, a proposito: los dos selectores del panel muestran a todos
 * y marcan a los ocupados en vez de esconderlos, asi que el frontend los lee igual (D-137).
 *
 * <p><b>Agregado 2026-09-16 (E-190).</b> Antes este record no tenia {@code cellId} porque el
 * endpoint solo devolvia aprendices SIN grupo y el campo hubiera sido siempre null. Desde que la
 * lista tambien ofrece el traslado, el dato es justamente lo que evita que el administrador mueva
 * a alguien sin saber de donde. */
public record AprendizCandidatoResponse(String userId, String fullName, String avatarUrl, String cellId) {

    public static AprendizCandidatoResponse from(AprendizCandidato candidato) {
        return new AprendizCandidatoResponse(candidato.userId().toString(), candidato.nombreCompleto(),
                candidato.avatarUrl(),
                candidato.celulaActual() != null ? candidato.celulaActual().toString() : null);
    }
}
