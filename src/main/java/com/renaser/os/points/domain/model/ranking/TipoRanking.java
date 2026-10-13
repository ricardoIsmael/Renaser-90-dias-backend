package com.renaser.os.points.domain.model.ranking;

import java.util.List;

public enum TipoRanking {
    GENERAL,
    COHORT,
    CELL,
    LEAGUE,
    /**
     * D-226: km acumulados del programa (hábito {@code DAILY_KM}), entre todos los aprendices activos.
     * En la base es el valor {@code KILOMETROS} de {@code tipo_ranking} (V85).
     */
    KILOMETROS,
    /**
     * D-266: días de gimnasio del programa (hábito {@code GYM}), entre todos los aprendices activos. En la base es
     * el valor {@code GIMNASIO} de {@code tipo_ranking} (V98).
     */
    GIMNASIO;

    /**
     * Los que genera el corte diario ({@code SnapshotRankingScheduler}) y la regeneración del panel, en
     * ese orden. Un solo lugar: antes eran dos listas copiadas, y un tipo nuevo tenía que acordarse de
     * las dos. COHORT sigue afuera: le falta el dato de cohorte.
     */
    public static final List<TipoRanking> CON_CORTE_DIARIO = List.of(LEAGUE, CELL, GENERAL, KILOMETROS, GIMNASIO);
}
