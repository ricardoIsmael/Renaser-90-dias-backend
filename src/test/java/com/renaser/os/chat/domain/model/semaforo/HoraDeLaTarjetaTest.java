package com.renaser.os.chat.domain.model.semaforo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cuándo sale la tarjeta y de qué día (D-223). Regla 02 §3: el reloj se fija en horas UTC que caen en el día
 * local ANTERIOR de Lima, que es donde se esconde el bug de E-91.
 */
class HoraDeLaTarjetaTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Test
    @DisplayName("04:50 UTC del 29 son las 23:50 del 28 en Lima: toca la tarjeta del 28, no la del día del servidor")
    void alas2350DeLimaTocaElDiaDeLima() {
        assertThat(HoraDeLaTarjeta.diaQueToca(Instant.parse("2026-09-29T04:50:00Z"), LIMA))
                .contains(LocalDate.of(2026, 9, 28));
    }

    @Test
    @DisplayName("04:59:59 UTC todavía es el 28 en Lima (el reintento de las 23:55 cae adentro)")
    void elReintentoCaeEnLaVentana() {
        assertThat(HoraDeLaTarjeta.diaQueToca(Instant.parse("2026-09-29T04:55:00Z"), LIMA))
                .contains(LocalDate.of(2026, 9, 28));
        assertThat(HoraDeLaTarjeta.diaQueToca(Instant.parse("2026-09-29T04:59:59Z"), LIMA))
                .contains(LocalDate.of(2026, 9, 28));
    }

    @Test
    @DisplayName("antes de las 23:50 locales no toca; pasada la medianoche, la de ayer ya no sale")
    void fueraDeLaVentanaNoToca() {
        assertThat(HoraDeLaTarjeta.diaQueToca(Instant.parse("2026-09-29T04:45:00Z"), LIMA)).isEmpty();
        assertThat(HoraDeLaTarjeta.diaQueToca(Instant.parse("2026-09-29T05:00:00Z"), LIMA)).isEmpty();
        assertThat(HoraDeLaTarjeta.diaQueToca(Instant.parse("2026-09-29T10:00:00Z"), LIMA)).isEmpty();
    }

    @Test
    @DisplayName("cada zona tiene su 23:50: a las 21:50 UTC es la hora en Madrid (UTC+2), no en Lima")
    void cadaZonaSuHora() {
        Instant ahora = Instant.parse("2026-09-28T21:50:00Z");
        assertThat(HoraDeLaTarjeta.diaQueToca(ahora, ZoneId.of("Europe/Madrid"))).contains(LocalDate.of(2026, 9, 28));
        assertThat(HoraDeLaTarjeta.diaQueToca(ahora, LIMA)).isEmpty();
    }
}
