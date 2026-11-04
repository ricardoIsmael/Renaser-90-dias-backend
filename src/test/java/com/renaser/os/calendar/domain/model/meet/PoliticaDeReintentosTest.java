package com.renaser.os.calendar.domain.model.meet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PoliticaDeReintentosTest {

    private static final Instant AHORA = Instant.parse("2026-11-05T15:00:00Z");

    // spec 040 FR-008
    @Test
    @DisplayName("FR-008: la espera es exponencial: 1, 2, 4, 8 minutos…")
    void esperaExponencial() {
        assertThat(espera(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(espera(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(espera(3)).isEqualTo(Duration.ofMinutes(4));
        assertThat(espera(4)).isEqualTo(Duration.ofMinutes(8));
    }

    // spec 040 FR-008
    @Test
    @DisplayName("FR-008: pasa de 15 minutos nunca")
    void topeDeQuinceMinutos() {
        assertThat(espera(5)).isEqualTo(Duration.ofMinutes(15));
        assertThat(espera(7)).isEqualTo(Duration.ofMinutes(15));
    }

    // spec 040 FR-008
    @Test
    @DisplayName("FR-008: con azar se suma como mucho un 25 % para que las filas no vuelvan todas juntas")
    void elAzarSumaPocoYNuncaResta() {
        Duration sinAzar = Duration.between(AHORA, PoliticaDeReintentos.proximoIntento(3, AHORA, 0.0));
        Duration conAzar = Duration.between(AHORA, PoliticaDeReintentos.proximoIntento(3, AHORA, 0.99));

        assertThat(conAzar).isGreaterThan(sinAzar).isLessThanOrEqualTo(sinAzar.plus(sinAzar.dividedBy(4)));
        assertThat(Duration.between(AHORA, PoliticaDeReintentos.proximoIntento(3, AHORA, -5.0))).isEqualTo(sinAzar);
    }

    // spec 040 FR-008
    @Test
    @DisplayName("FR-008: máximo 8 intentos; después ya no hay próximo")
    void maximoOchoIntentos() {
        assertThat(PoliticaDeReintentos.MAXIMO_DE_INTENTOS).isEqualTo(8);
        assertThat(PoliticaDeReintentos.proximoIntento(7, AHORA, 0.0)).isNotNull();
        assertThat(PoliticaDeReintentos.proximoIntento(8, AHORA, 0.0)).isNull();
        assertThat(PoliticaDeReintentos.proximoIntento(20, AHORA, 0.0)).isNull();
    }

    private static Duration espera(int intentos) {
        return Duration.between(AHORA, PoliticaDeReintentos.proximoIntento(intentos, AHORA, 0.0));
    }
}
