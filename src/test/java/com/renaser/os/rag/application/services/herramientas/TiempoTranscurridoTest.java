package com.renaser.os.rag.application.services.herramientas;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TiempoTranscurridoTest {

    private static final Instant AHORA = Instant.parse("2026-09-24T03:00:00Z");

    @Test
    @DisplayName("minutos, horas y dias redondeados hacia abajo; nunca negativo")
    void escala() {
        assertThat(TiempoTranscurrido.desde(AHORA.minusSeconds(30), AHORA)).isEqualTo("hace menos de 1 min");
        assertThat(TiempoTranscurrido.desde(AHORA.minusSeconds(59 * 60 + 59), AHORA)).isEqualTo("hace 59 min");
        assertThat(TiempoTranscurrido.desde(AHORA.minusSeconds(23 * 3600 + 3599), AHORA)).isEqualTo("hace 23 h");
        assertThat(TiempoTranscurrido.desde(AHORA.minusSeconds(24 * 3600), AHORA)).isEqualTo("hace 1 dia");
        assertThat(TiempoTranscurrido.desde(AHORA.minusSeconds(3 * 86400), AHORA)).isEqualTo("hace 3 dias");
        assertThat(TiempoTranscurrido.desde(AHORA.plusSeconds(600), AHORA)).isEqualTo("hace menos de 1 min");
    }

    @Test
    @DisplayName("recorta en un espacio y marca el corte; un texto corto queda igual")
    void recorte() {
        assertThat(TiempoTranscurrido.recortado("  corto  ", 10)).isEqualTo("corto");
        assertThat(TiempoTranscurrido.recortado("uno dos tres cuatro", 12)).isEqualTo("uno dos tres...");
        assertThat(TiempoTranscurrido.recortado(null, 5)).isEmpty();
    }
}
