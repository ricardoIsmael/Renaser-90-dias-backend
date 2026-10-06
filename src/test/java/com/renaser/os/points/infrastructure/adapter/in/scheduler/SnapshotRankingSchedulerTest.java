package com.renaser.os.points.infrastructure.adapter.in.scheduler;

import com.renaser.os.points.domain.model.ranking.TipoRanking;
import com.renaser.os.shared.domain.FixedClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-561. La foto del ranking es UNA tabla comun para todo el padron, con una sola fecha (el dia de Lima, como lo
 * dice {@code RankingController.hoyDelPadron}), no una por participante. Se genera a las 05:05 UTC = 00:05 de
 * Lima, y a esa hora la fecha UTC y la de Lima son la misma: esta prueba lo fija, en la madrugada UTC.
 */
class SnapshotRankingSchedulerTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private record Corte(TipoRanking tipo, LocalDate fecha) {
    }

    @Test
    void aLas0505UtcGeneraTodosLosTiposConElDiaQueRecienEmpezoEnLima() {
        List<Corte> cortes = new ArrayList<>();
        Instant ahora = Instant.parse("2026-10-06T05:05:00Z");
        var scheduler = new SnapshotRankingScheduler((tipo, fecha) -> cortes.add(new Corte(tipo, fecha)),
                FixedClock.at(ahora));

        scheduler.generarSnapshotsDelDia();

        LocalDate diaDeLima = ahora.atZone(LIMA).toLocalDate();
        assertThat(diaDeLima).isEqualTo(LocalDate.of(2026, 10, 6));
        assertThat(cortes).extracting(Corte::fecha).containsOnly(diaDeLima);
        assertThat(cortes).extracting(Corte::tipo).containsExactlyElementsOf(TipoRanking.CON_CORTE_DIARIO);
    }

    @Test
    void unTipoQueFallaNoImpideGenerarLosDemas() {
        List<TipoRanking> generados = new ArrayList<>();
        var scheduler = new SnapshotRankingScheduler((tipo, fecha) -> {
            if (tipo == TipoRanking.CON_CORTE_DIARIO.get(0)) {
                throw new IllegalStateException("fallo");
            }
            generados.add(tipo);
        }, FixedClock.at(Instant.parse("2026-10-06T05:05:00Z")));

        scheduler.generarSnapshotsDelDia();

        assertThat(generados).containsExactlyElementsOf(
                TipoRanking.CON_CORTE_DIARIO.subList(1, TipoRanking.CON_CORTE_DIARIO.size()));
    }
}
