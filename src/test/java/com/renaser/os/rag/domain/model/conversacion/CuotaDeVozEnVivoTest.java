package com.renaser.os.rag.domain.model.conversacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CuotaDeVozEnVivoTest {

    private final CuotaDeVozEnVivo cuota = new CuotaDeVozEnVivo(Duration.ofMinutes(10), Duration.ofMinutes(15));

    @Test
    @DisplayName("lo que queda es el limite menos lo usado, y nunca negativo")
    void restante() {
        assertThat(cuota.restante(Duration.ofMinutes(4))).isEqualTo(Duration.ofMinutes(6));
        assertThat(cuota.restante(Duration.ofMinutes(12))).isEqualTo(Duration.ZERO);
        assertThat(cuota.agotada(Duration.ofMinutes(10))).isTrue();
        assertThat(cuota.agotada(Duration.ofMinutes(9).plusSeconds(59))).isFalse();
    }

    @Test
    @DisplayName("a las 03:00 UTC, en Lima todavia es el dia anterior (regla 02)")
    void diaLocal() {
        Instant tresAmUtc = Instant.parse("2026-09-24T03:00:00Z");

        assertThat(CuotaDeVozEnVivo.diaDe(tresAmUtc, ZoneId.of("America/Lima"))).isEqualTo(LocalDate.parse("2026-09-23"));
        assertThat(CuotaDeVozEnVivo.diaDe(Instant.parse("2026-09-24T05:00:00Z"), ZoneId.of("America/Lima")))
                .isEqualTo(LocalDate.parse("2026-09-24"));
    }

    @Test
    @DisplayName("la sesion vence al llegar exactamente a su maximo")
    void sesionVencida() {
        Instant inicio = Instant.parse("2026-09-24T03:00:00Z");

        assertThat(cuota.sesionVencida(inicio, inicio.plus(Duration.ofMinutes(15)).minusMillis(1))).isFalse();
        assertThat(cuota.sesionVencida(inicio, inicio.plus(Duration.ofMinutes(15)))).isTrue();
    }

    @Test
    @DisplayName("una sesion de duracion cero no es valida")
    void limitesInvalidos() {
        assertThatThrownBy(() -> new CuotaDeVozEnVivo(Duration.ofMinutes(10), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
