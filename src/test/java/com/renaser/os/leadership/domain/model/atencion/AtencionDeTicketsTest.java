package com.renaser.os.leadership.domain.model.atencion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AtencionDeTicketsTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T15:00:00Z");

    @Test
    @DisplayName("mediana y no promedio: doce respuestas de 1 h y una de tres semanas dan 1 h")
    void medianaNoPromedio() {
        List<Duration> respuestas = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            respuestas.add(Duration.ofHours(1));
        }
        respuestas.add(Duration.ofDays(21));

        AtencionDeTickets atencion = AtencionDeTickets.medir(List.of(), respuestas, AHORA);

        assertThat(atencion.medianaHoras()).isEqualByComparingTo(new BigDecimal("1.0"));
        assertThat(atencion.respondidas()).isEqualTo(13);
    }

    @Test
    @DisplayName("con cantidad par, la mediana es el punto medio de las dos centrales")
    void medianaPar() {
        AtencionDeTickets atencion = AtencionDeTickets.medir(List.of(),
                List.of(Duration.ofHours(2), Duration.ofHours(5), Duration.ofMinutes(30), Duration.ofHours(10)), AHORA);

        assertThat(atencion.medianaHoras()).isEqualByComparingTo(new BigDecimal("3.5"));
    }

    @Test
    @DisplayName("sin respuestas no hay mediana: null, nunca 0 h (RL-05)")
    void sinRespuestasNoEsCero() {
        AtencionDeTickets atencion = AtencionDeTickets.medir(List.of(), List.of(), AHORA);

        assertThat(atencion.medianaHoras()).isNull();
        assertThat(atencion.diasDelMasAntiguo()).isNull();
        assertThat(atencion.pendientes()).isZero();
    }

    @Test
    @DisplayName("el pendiente mas viejo se cuenta en dias completos")
    void pendienteMasViejo() {
        AtencionDeTickets atencion = AtencionDeTickets.medir(
                List.of(AHORA.minus(Duration.ofHours(30)), AHORA.minus(Duration.ofDays(6).plusHours(2))), List.of(), AHORA);

        assertThat(atencion.pendientes()).isEqualTo(2);
        assertThat(atencion.diasDelMasAntiguo()).isEqualTo(6);
    }
}
