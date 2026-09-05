package com.renaser.os.habits.domain.model.registro;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La escala de puntos en los instantes exactos que nombro el dueno del producto, textual
 * (2026-09-04): "3h a 10 puntos y luego, dentro de los 10 minutos, cada 2 minutos se resta
 * 1 punto, en total ganando 5".
 *
 * <p>Historia de este archivo: nacio como prueba de CARACTERIZACION de la regla heredada
 * (points.ts), que tenia los dos tramos en el orden inverso, para que la discrepancia con lo que
 * el dueno describia quedara visible. El dueno confirmo su lectura y la regla se invirtio (D-97);
 * estas afirmaciones son las que se dieron vuelta. Si vuelven a fallar, la escala cambio otra vez.
 */
@DisplayName("Escala de puntos en los instantes que nombro el dueno del producto (D-97)")
class ResultadoOtorgamientoVentanaTresHorasTest {

    private static final Instant ANCLA = Instant.parse("2026-09-04T20:00:00Z");
    private static final Duration EXTENSION = Duration.ofHours(VentanaEntrega.EXTENSION_DEFAULT_HORAS);

    private static ResultadoOtorgamiento alMinuto(long minutosTarde) {
        return ResultadoOtorgamiento.calcular(ANCLA, ANCLA.plus(Duration.ofMinutes(minutosTarde)), EXTENSION);
    }

    @Test
    void justoEnLaHora() {
        assertThat(alMinuto(0).fase()).isEqualTo(FaseOtorgamiento.A_TIEMPO);
        assertThat(alMinuto(0).puntos()).isEqualTo(10);
    }

    @Test
    void aDosHorasCincuentaYNueve() {
        assertThat(alMinuto(179).fase()).isEqualTo(FaseOtorgamiento.EXTENDIDO);
        assertThat(alMinuto(179).puntos()).isEqualTo(10);
    }

    @Test
    void aTresHorasExactas() {
        assertThat(alMinuto(180).fase()).isEqualTo(FaseOtorgamiento.EXTENDIDO);
        assertThat(alMinuto(180).puntos()).isEqualTo(10);
    }

    @Test
    void aTresHorasDosMinutos() {
        assertThat(alMinuto(182).fase()).isEqualTo(FaseOtorgamiento.GRACIA);
        assertThat(alMinuto(182).puntos()).isEqualTo(9);
    }

    @Test
    void aTresHorasSeisMinutos() {
        assertThat(alMinuto(186).fase()).isEqualTo(FaseOtorgamiento.GRACIA);
        assertThat(alMinuto(186).puntos()).isEqualTo(7);
    }

    @Test
    @DisplayName("a las 3 h 10 min: 5 puntos, el borde que el dueno cito de memoria")
    void aTresHorasDiezMinutos() {
        assertThat(alMinuto(190).fase()).isEqualTo(FaseOtorgamiento.GRACIA);
        assertThat(alMinuto(190).puntos()).isEqualTo(5);
    }

    @Test
    void aTresHorasOnceMinutos() {
        assertThat(alMinuto(191).fase()).isEqualTo(FaseOtorgamiento.EXPIRADO);
        assertThat(alMinuto(191).puntos()).isZero();
    }

    @Test
    @DisplayName("escalonado, no proporcional: a los 3 minutos de gracia siguen siendo 9")
    void escalonadoNoProporcional() {
        assertThat(alMinuto(181).puntos()).isEqualTo(10); // floor(1/2) = 0
        assertThat(alMinuto(182).puntos()).isEqualTo(9);
        assertThat(alMinuto(183).puntos()).isEqualTo(9);  // floor(3/2) = 1
        assertThat(alMinuto(184).puntos()).isEqualTo(8);
        assertThat(alMinuto(188).puntos()).isEqualTo(6);
        assertThat(alMinuto(190).puntos()).isEqualTo(5);
    }
}
