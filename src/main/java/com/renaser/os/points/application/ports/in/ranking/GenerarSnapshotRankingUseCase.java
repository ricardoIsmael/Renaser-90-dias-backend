package com.renaser.os.points.application.ports.in.ranking;

import com.renaser.os.points.domain.model.ranking.TipoRanking;

import java.time.LocalDate;

public interface GenerarSnapshotRankingUseCase {

    void generar(TipoRanking tipo, LocalDate fecha);
}
