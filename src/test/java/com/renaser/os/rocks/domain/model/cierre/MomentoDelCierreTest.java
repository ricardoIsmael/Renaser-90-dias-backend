package com.renaser.os.rocks.domain.model.cierre;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** El instante del cierre y la hora de pared de la persona (spec 017, corregida el 2026-10-30). */
class MomentoDelCierreTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Test
    @DisplayName("la hora local sale de la zona de la persona, no de la del servidor")
    void horaLocal() {
        Instant madrugadaUtc = Instant.parse("2026-10-29T03:00:00Z");

        assertThat(MomentoDelCierre.de(madrugadaUtc, LIMA).horaLocal()).isEqualTo(LocalTime.of(22, 0));
        assertThat(MomentoDelCierre.de(madrugadaUtc, ZoneId.of("Asia/Tokyo")).horaLocal()).isEqualTo(LocalTime.of(12, 0));
        assertThat(MomentoDelCierre.de(madrugadaUtc, LIMA).instante()).isEqualTo(madrugadaUtc);
    }

    @Test
    @DisplayName("la hora llegó si es igual o anterior a la actual; una posterior no; sin hora cuenta como llegada")
    void llegoLaHora() {
        MomentoDelCierre aLas21 = new MomentoDelCierre(Instant.parse("2026-10-29T02:00:00Z"), LocalTime.of(21, 0));

        assertThat(aLas21.llegoLaHora(LocalTime.of(20, 30))).isTrue();
        assertThat(aLas21.llegoLaHora(LocalTime.of(21, 0))).isTrue();
        assertThat(aLas21.llegoLaHora(LocalTime.of(21, 1))).isFalse();
        assertThat(aLas21.llegoLaHora(null)).isTrue();
    }
}
