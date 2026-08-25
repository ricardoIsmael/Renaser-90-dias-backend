package com.renaser.os.points.application.ports.out.ranking;

import com.renaser.os.points.domain.model.ranking.PosicionRanking;
import com.renaser.os.points.domain.model.ranking.TipoRanking;

import java.time.LocalDate;
import java.util.List;

public interface SaveRankingSnapshotPort {

    /**
     * Reemplaza atómicamente el snapshot de un tipo+fecha (borra lo que hubiera y guarda
     * `posiciones`) — hace idempotente re-correr el scheduler el mismo día sin duplicar
     * ni dejar posiciones viejas huérfanas.
     */
    void reemplazar(TipoRanking tipo, LocalDate fecha, List<PosicionRanking> posiciones);
}
