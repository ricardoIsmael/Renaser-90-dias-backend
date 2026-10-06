package com.renaser.os.rag.domain.model.espejosombra;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-560. Los casos de Lima fijan lo que hacia el cron viejo (lunes 03:00 UTC, {@code clock.today()}, "la semana
 * pasada"): son los que tienen que pasar igual con el codigo viejo y con el nuevo. Los de otras zonas son los que
 * el cron viejo hacia mal. Todos los relojes caen en la madrugada UTC o a medio dia, nunca solo a las 10:00 UTC.
 */
class SemanaDelInformeTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final ZoneId LOS_ANGELES = ZoneId.of("America/Los_Angeles");
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final ZoneId TOKIO = ZoneId.of("Asia/Tokyo");

    /** El lunes 28/09/2026: la semana que termina el domingo 04/10/2026. */
    private static final LocalDate SEMANA_DEL_28_SEP = LocalDate.of(2026, 9, 28);

    private static Instant utc(String instante) {
        return Instant.parse(instante);
    }

    @Test
    void limaElLunes0300UtcEsSuDomingo2200YLeTocaLaSemanaQueTerminaEseDomingo() {
        assertThat(SemanaDelInforme.quePideGenerarseEn(LIMA, utc("2026-10-05T03:00:00Z")))
                .contains(new SemanaDelInforme(SEMANA_DEL_28_SEP));
    }

    @Test
    void limaUnMinutoAntesDeSuDomingo2200TodaviaNoLeTocaNada() {
        assertThat(SemanaDelInforme.quePideGenerarseEn(LIMA, utc("2026-10-05T02:59:00Z"))).isEmpty();
    }

    @Test
    void limaPuedePonerseAlDiaTodoUnDiaDespuesDelCorteYNoMas() {
        assertThat(SemanaDelInforme.quePideGenerarseEn(LIMA, utc("2026-10-06T02:59:00Z")))
                .contains(new SemanaDelInforme(SEMANA_DEL_28_SEP));
        assertThat(SemanaDelInforme.quePideGenerarseEn(LIMA, utc("2026-10-06T03:00:00Z"))).isEmpty();
    }

    @Test
    void losAngelesNoEsperaAlLunes0300UtcQueParaEllaEsDomingo1900() {
        assertThat(SemanaDelInforme.quePideGenerarseEn(LOS_ANGELES, utc("2026-10-05T03:00:00Z"))).isEmpty();
        assertThat(SemanaDelInforme.quePideGenerarseEn(LOS_ANGELES, utc("2026-10-05T00:30:00Z"))).isEmpty();
    }

    @Test
    void losAngelesLeTocaSuDomingo2200QueEsElLunes0600Utc() {
        assertThat(SemanaDelInforme.quePideGenerarseEn(LOS_ANGELES, utc("2026-10-05T06:00:00Z")))
                .contains(new SemanaDelInforme(SEMANA_DEL_28_SEP));
    }

    @Test
    void tokioYaPasoSuCorteElDomingoAMedioDiaUtcYElLunes0300UtcSigueEnSuMargen() {
        assertThat(SemanaDelInforme.quePideGenerarseEn(TOKIO, utc("2026-10-04T13:00:00Z")))
                .contains(new SemanaDelInforme(SEMANA_DEL_28_SEP));
        assertThat(SemanaDelInforme.quePideGenerarseEn(TOKIO, utc("2026-10-05T00:30:00Z")))
                .contains(new SemanaDelInforme(SEMANA_DEL_28_SEP));
    }

    @Test
    void tokioAntesDeSuDomingo2200TodaviaNoLeTocaLaSemanaEnCurso() {
        assertThat(SemanaDelInforme.quePideGenerarseEn(TOKIO, utc("2026-10-04T12:59:00Z"))).isEmpty();
    }

    @Test
    void madridUsaSuHoraLocalConElCambioDeHorarioDeInvierno() {
        // 25/10/2026: Madrid vuelve a UTC+1. Su domingo 22:00 es 21:00 UTC y la semana es la del 19/10.
        assertThat(SemanaDelInforme.quePideGenerarseEn(MADRID, utc("2026-10-25T20:59:00Z"))).isEmpty();
        assertThat(SemanaDelInforme.quePideGenerarseEn(MADRID, utc("2026-10-25T21:00:00Z")))
                .contains(new SemanaDelInforme(LocalDate.of(2026, 10, 19)));
    }

    @Test
    void madridAntesDelCambioDeHorarioSuCorteEsA2000Utc() {
        assertThat(SemanaDelInforme.quePideGenerarseEn(MADRID, utc("2026-10-04T20:00:00Z")))
                .contains(new SemanaDelInforme(SEMANA_DEL_28_SEP));
    }

    @Test
    void entreSemanaNoLeTocaNadaANadie() {
        assertThat(SemanaDelInforme.quePideGenerarseEn(LIMA, utc("2026-10-08T15:00:00Z"))).isEmpty();
        assertThat(SemanaDelInforme.quePideGenerarseEn(TOKIO, utc("2026-10-08T15:00:00Z"))).isEmpty();
    }
}
