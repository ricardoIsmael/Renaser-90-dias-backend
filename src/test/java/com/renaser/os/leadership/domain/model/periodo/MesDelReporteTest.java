package com.renaser.os.leadership.domain.model.periodo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MesDelReporteTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Test
    @DisplayName("regla 02: a las 03:00 UTC del 1 de octubre en Lima sigue siendo septiembre")
    void elMesEsElDeLaZonaNoElDelServidor() {
        Instant ahora = Instant.parse("2026-10-01T03:00:00Z");

        MesDelReporte mes = MesDelReporte.enCurso(LIMA, ahora);

        assertThat(mes.mes()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(mes.desde()).isEqualTo(Instant.parse("2026-09-01T05:00:00Z"));
        assertThat(mes.corte()).isEqualTo(ahora);
        assertThat(mes.hasta()).isEqualTo(Instant.parse("2026-10-01T05:00:00Z"));
        assertThat(mes.cerrado(ahora)).isFalse();
    }

    @Test
    @DisplayName("un mes pasado va entero [inicio, fin) en la zona y esta cerrado")
    void mesPasado() {
        Instant ahora = Instant.parse("2026-10-15T12:00:00Z");

        MesDelReporte mes = MesDelReporte.pedido("2026-02", LIMA, ahora);

        assertThat(mes.desde()).isEqualTo(Instant.parse("2026-02-01T05:00:00Z"));
        assertThat(mes.corte()).isEqualTo(Instant.parse("2026-03-01T05:00:00Z"));
        assertThat(mes.cerrado(ahora)).isTrue();
        assertThat(mes.contiene(Instant.parse("2026-03-01T04:59:59Z"))).isTrue();
        assertThat(mes.contiene(Instant.parse("2026-03-01T05:00:00Z"))).isFalse();
    }

    @Test
    @DisplayName("sin mes pedido es el mes en curso; un mes futuro o mal escrito es 400")
    void validaciones() {
        Instant ahora = Instant.parse("2026-10-15T12:00:00Z");

        assertThat(MesDelReporte.pedido(null, LIMA, ahora).mes()).isEqualTo(YearMonth.of(2026, 10));
        assertThatThrownBy(() -> MesDelReporte.pedido("2026-11", LIMA, ahora))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("todavia no empezo");
        assertThatThrownBy(() -> MesDelReporte.pedido("octubre", LIMA, ahora))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("AAAA-MM");
    }
}
