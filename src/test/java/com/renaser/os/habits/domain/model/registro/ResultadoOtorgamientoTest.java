package com.renaser.os.habits.domain.model.registro;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La escala de puntos tramo por tramo, con el orden que definio el dueno (D-97, 2026-09-04):
 * 3 horas a puntaje completo y DESPUES los 10 minutos de decaimiento. Antes de D-97 este archivo
 * afirmaba el orden inverso (points.ts:100-125 traducido 1:1); si estas afirmaciones vuelven a
 * fallar, alguien dio vuelta la regla otra vez y hay que ir a MODULOS_A_AVANZAR.md a ver por que.
 */
class ResultadoOtorgamientoTest {

    private static final Instant ANCLA = Instant.parse("2026-08-24T20:00:00Z");
    private static final Duration EXTENSION = Duration.ofHours(3);

    @Test
    @DisplayName("entregado exactamente a tiempo -> A_TIEMPO, 10 puntos")
    void aTiempoExacto() {
        var r = ResultadoOtorgamiento.calcular(ANCLA, ANCLA, EXTENSION);
        assertThat(r.fase()).isEqualTo(FaseOtorgamiento.A_TIEMPO);
        assertThat(r.puntos()).isEqualTo(10);
    }

    @Test
    @DisplayName("entregado antes de la hora fin -> A_TIEMPO, 10 puntos")
    void aTiempoAntes() {
        var r = ResultadoOtorgamiento.calcular(ANCLA, ANCLA.minus(Duration.ofHours(1)), EXTENSION);
        assertThat(r.fase()).isEqualTo(FaseOtorgamiento.A_TIEMPO);
        assertThat(r.puntos()).isEqualTo(10);
    }

    @Test
    @DisplayName("+1 seg ya es EXTENDIDO, pero sigue valiendo 10: las 3 horas son a puntaje completo")
    void extendidoJustoDespuesDelAncla() {
        var r = ResultadoOtorgamiento.calcular(ANCLA, ANCLA.plusSeconds(1), EXTENSION);
        assertThat(r.fase()).isEqualTo(FaseOtorgamiento.EXTENDIDO);
        assertThat(r.puntos()).isEqualTo(10);
    }

    @Test
    @DisplayName("a las 3 h exactas -> todavia EXTENDIDO, 10 puntos")
    void extendidoAlBordeDeLaExtension() {
        var r = ResultadoOtorgamiento.calcular(ANCLA, ANCLA.plus(EXTENSION), EXTENSION);
        assertThat(r.fase()).isEqualTo(FaseOtorgamiento.EXTENDIDO);
        assertThat(r.puntos()).isEqualTo(10);
    }

    @Test
    @DisplayName("3 h + 1 min (dentro del paso de 2) -> GRACIA, sigue en 10 (floor(1/2)=0)")
    void graciaUnMinutoNoDescuenta() {
        var r = ResultadoOtorgamiento.calcular(ANCLA, ANCLA.plus(EXTENSION).plus(Duration.ofMinutes(1)), EXTENSION);
        assertThat(r.fase()).isEqualTo(FaseOtorgamiento.GRACIA);
        assertThat(r.puntos()).isEqualTo(10);
    }

    @Test
    @DisplayName("3 h + 2 min -> 9 puntos (10 - floor(2/2))")
    void gracia2Minutos() {
        var r = ResultadoOtorgamiento.calcular(ANCLA, ANCLA.plus(EXTENSION).plus(Duration.ofMinutes(2)), EXTENSION);
        assertThat(r.fase()).isEqualTo(FaseOtorgamiento.GRACIA);
        assertThat(r.puntos()).isEqualTo(9);
    }

    @Test
    @DisplayName("3 h + 10 min exactos -> piso de gracia, 5 puntos — el numero que cito el dueno")
    void graciaDiezMinutosPiso() {
        var r = ResultadoOtorgamiento.calcular(ANCLA, ANCLA.plus(EXTENSION).plus(Duration.ofMinutes(10)), EXTENSION);
        assertThat(r.fase()).isEqualTo(FaseOtorgamiento.GRACIA);
        assertThat(r.puntos()).isEqualTo(5);
    }

    @Test
    @DisplayName("3 h + 10 min + 1 seg -> EXPIRADO, 0 puntos")
    void expiradoPasadoTodoElPlazo() {
        Instant pasado = ANCLA.plus(EXTENSION).plus(Duration.ofMinutes(10)).plusSeconds(1);
        var r = ResultadoOtorgamiento.calcular(ANCLA, pasado, EXTENSION);
        assertThat(r.fase()).isEqualTo(FaseOtorgamiento.EXPIRADO);
        assertThat(r.puntos()).isEqualTo(0);
    }

    @Test
    @DisplayName("extension en 0 (ventana recortada por la medianoche): la gracia arranca en el ancla")
    void extensionCeroLaGraciaArrancaEnElAncla() {
        var r = ResultadoOtorgamiento.calcular(ANCLA, ANCLA.plus(Duration.ofMinutes(4)), Duration.ZERO);
        assertThat(r.fase()).isEqualTo(FaseOtorgamiento.GRACIA);
        assertThat(r.puntos()).isEqualTo(8);
        var caido = ResultadoOtorgamiento.calcular(ANCLA, ANCLA.plus(Duration.ofMinutes(11)), Duration.ZERO);
        assertThat(caido.fase()).isEqualTo(FaseOtorgamiento.EXPIRADO);
    }
}
