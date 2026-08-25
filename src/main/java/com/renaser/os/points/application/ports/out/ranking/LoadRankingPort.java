package com.renaser.os.points.application.ports.out.ranking;

import com.renaser.os.points.domain.model.ranking.TipoRanking;
import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface LoadRankingPort {

    List<EntradaRankingConNombre> porTipoYFecha(TipoRanking tipo, LocalDate fecha);

    record EntradaRankingConNombre(UserId participanteId, String fullName, int posicion, BigDecimal puntaje) {
    }
}
