package com.renaser.os.points.application.ports.out.ranking;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.util.List;

public interface LoadRankingCandidatosPort {

    List<CandidatoRanking> aprendicesActivosConPuntaje();

    record CandidatoRanking(UserId participanteId, String fullName, int puntosLiga, BigDecimal coherencia) {
    }
}
