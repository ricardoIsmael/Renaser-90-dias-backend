package com.renaser.os.points.infrastructure.adapter.in.rest.ranking;

import com.renaser.os.points.application.ports.in.ranking.ConsultarRankingUseCase.EntradaRanking;

import java.math.BigDecimal;

public record EntradaRankingResponse(String participanteId, String fullName, int posicion, BigDecimal puntaje) {

    public static EntradaRankingResponse from(EntradaRanking entrada) {
        return new EntradaRankingResponse(entrada.participanteId().toString(), entrada.fullName(),
                entrada.posicion(), entrada.puntaje());
    }
}
