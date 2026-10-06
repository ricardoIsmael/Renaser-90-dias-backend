package com.renaser.os.rag.domain.model.espejosombra;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-560. Regla del dueño (2026-10-06): el corte es la medianoche del domingo (lunes 00:00) en la zona de cada
 * participante, con la semana completa. Antes era el domingo 22:00 local (lunes 03:00 UTC en Lima); estos casos de
 * Lima decian ese instante y se movieron a 05:00 UTC por esa decision. Los relojes caen en la madrugada UTC.
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
    void limaElLunes0500UtcEsSuMedianocheDelDomingoYLeTocaLaSemanaCompleta() {
        assertThat(SemanaDelInforme.quePideGenerarseEn(LIMA, utc("2026-10-05T05:00:00Z")))
                .contains(new SemanaDelInforme(SEMANA_DEL_28_SEP));
    }

    @Test
    void limaElLunes0300UtcYaNoGeneraPorqueSigueSiendoDomingo2200() {
        assertThat(SemanaDelInforme.quePideGenerarseEn(LIMA, utc("2026-10-05T03:00:00Z"))).isEmpty();
        assertThat(SemanaDelInforme.quePideGenerarseEn(LIMA, utc("2026-10-05T04:59:00Z"))).isEmpty();
    }

    @Test
    void limaPuedePonerseAlDiaTodoUnDiaDespuesDelCorteYNoMas() {
        assertThat(SemanaDelInforme.quePideGenerarseEn(LIMA, utc("2026-10-06T04:59:00Z")))
                .contains(new SemanaDelInforme(SEMANA_DEL_28_SEP));
        assertThat(SemanaDelInforme.quePideGenerarseEn(LIMA, utc("2026-10-06T05:00:00Z"))).isEmpty();
    }

    @Test
    void losAngelesNoEsperaAlLunes0500UtcQueParaEllaEsDomingo2100() {
        assertThat(SemanaDelInforme.quePideGenerarseEn(LOS_ANGELES, utc("2026-10-05T05:00:00Z"))).isEmpty();
        assertThat(SemanaDelInforme.quePideGenerarseEn(LOS_ANGELES, utc("2026-10-05T00:30:00Z"))).isEmpty();
    }

    @Test
    void losAngelesLeTocaSuMedianocheQueEsElLunes0700Utc() {
        assertThat(SemanaDelInforme.quePideGenerarseEn(LOS_ANGELES, utc("2026-10-05T06:59:00Z"))).isEmpty();
        assertThat(SemanaDelInforme.quePideGenerarseEn(LOS_ANGELES, utc("2026-10-05T07:00:00Z")))
                .contains(new SemanaDelInforme(SEMANA_DEL_28_SEP));
    }

    @Test
    void tokioYaPasoSuMedianocheDelDomingoALas1500UtcYElLunes0300UtcSigueEnSuMargen() {
        assertThat(SemanaDelInforme.quePideGenerarseEn(TOKIO, utc("2026-10-04T14:59:00Z"))).isEmpty();
        assertThat(SemanaDelInforme.quePideGenerarseEn(TOKIO, utc("2026-10-04T15:00:00Z")))
                .contains(new SemanaDelInforme(SEMANA_DEL_28_SEP));
        assertThat(SemanaDelInforme.quePideGenerarseEn(TOKIO, utc("2026-10-05T03:00:00Z")))
                .contains(new SemanaDelInforme(SEMANA_DEL_28_SEP));
    }

    @Test
    void madridUsaSuHoraLocalConElCambioDeHorarioDeInvierno() {
        // 25/10/2026: Madrid vuelve a UTC+1 a las 03:00 locales. Su lunes 00:00 es 23:00 UTC del domingo.
        assertThat(SemanaDelInforme.quePideGenerarseEn(MADRID, utc("2026-10-25T22:59:00Z"))).isEmpty();
        assertThat(SemanaDelInforme.quePideGenerarseEn(MADRID, utc("2026-10-25T23:00:00Z")))
                .contains(new SemanaDelInforme(LocalDate.of(2026, 10, 19)));
    }

    @Test
    void madridAntesDelCambioDeHorarioSuCorteEsA2200Utc() {
        assertThat(SemanaDelInforme.quePideGenerarseEn(MADRID, utc("2026-10-04T22:00:00Z")))
                .contains(new SemanaDelInforme(SEMANA_DEL_28_SEP));
    }

    @Test
    void entreSemanaNoLeTocaNadaANadie() {
        assertThat(SemanaDelInforme.quePideGenerarseEn(LIMA, utc("2026-10-08T15:00:00Z"))).isEmpty();
        assertThat(SemanaDelInforme.quePideGenerarseEn(TOKIO, utc("2026-10-08T15:00:00Z"))).isEmpty();
    }
}
