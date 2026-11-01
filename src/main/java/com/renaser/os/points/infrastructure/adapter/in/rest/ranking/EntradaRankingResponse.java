package com.renaser.os.points.infrastructure.adapter.in.rest.ranking;

import com.renaser.os.points.application.ports.in.ranking.ConsultarRankingUseCase.EntradaRanking;

import java.math.BigDecimal;

/** {@code formation} (spec 024): la formación de la fila; {@code posicion} es la posición DENTRO de ella. Un cliente anterior lo ignora. */
public record EntradaRankingResponse(String participanteId, String fullName, int posicion, BigDecimal puntaje,
                                      String formation) {

    public static EntradaRankingResponse from(EntradaRanking entrada) {
        return new EntradaRankingResponse(entrada.participanteId().toString(), entrada.fullName(),
                entrada.posicion(), entrada.puntaje(), entrada.formacion().codigo());
    }
}
