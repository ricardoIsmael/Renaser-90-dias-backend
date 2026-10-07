package com.renaser.os.chat.infrastructure.adapter.in.rest.ranking;

import com.renaser.os.chat.application.ports.in.ranking.PublicarPodioDeLaSemanaUseCase.ResultadoDelPodio;

import java.time.LocalDate;

/** @param status {@code PUBLICADO}, {@code YA_ESTABA}, {@code SIN_PUNTAJES} o {@code SIN_GRUPO_GENERAL} (D-262) */
public record PublicacionDelPodioResponse(LocalDate weekStart, LocalDate weekEnd, String status, int messages) {

    static PublicacionDelPodioResponse from(ResultadoDelPodio resultado) {
        return new PublicacionDelPodioResponse(resultado.lunes(), resultado.domingo(), resultado.estado().name(),
                resultado.piezas());
    }
}
