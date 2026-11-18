package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * {@code POST /api/v1/leadership/people/{personId}/documento}.
 *
 * @param tipo        {@code APRENDIZ} (la semana de un aprendiz, o la propia de un mentor que sigue su programa) o {@code MENTOR} (la de su grupo)
 * @param semanaHasta el viernes que cierra la semana; opcional: sin él, la semana en curso de la persona
 */
public record PedirDocumentoDePersonaRequest(@NotNull Tipo tipo, LocalDate semanaHasta) {

    public enum Tipo {
        APRENDIZ,
        MENTOR
    }
}
