package com.renaser.os.habits.domain.model.registro;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** points.ts:100-125 traducido 1:1 — ver docs/MODULO_HABITS.md paso 0, §2.1 de MODULO_POINTS.md. */
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
    @DisplayName("+2 min de gracia -> 9 puntos (10 - floor(2/2))")
    void gracia2Minutos() {
        var r = ResultadoOtorgamiento.calcular(ANCLA, ANCLA.plus(Duration.ofMinutes(2)), EXTENSION);
        assertThat(r.fase()).isEqualTo(FaseOtorgamiento.GRACIA);
        assertThat(r.puntos()).isEqualTo(9);
    }

    @Test
    @DisplayName("+1 min (dentro del paso de 2) -> sigue en 10 (floor(1/2)=0)")
    void graciaUnMinutoNoDescuenta() {
        var r = ResultadoOtorgamiento.calcular(ANCLA, ANCLA.plus(Duration.ofMinutes(1)), EXTENSION);
        assertThat(r.fase()).isEqualTo(FaseOtorgamiento.GRACIA);
        assertThat(r.puntos()).isEqualTo(10);
    }

    @Test
    @DisplayName("+10 min exactos -> piso de gracia, 5 puntos (10 - floor(10/2))")
    void graciaDiezMinutosPiso() {
        var r = ResultadoOtorgamiento.calcular(ANCLA, ANCLA.plus(Duration.ofMinutes(10)), EXTENSION);
        assertThat(r.fase()).isEqualTo(FaseOtorgamiento.GRACIA);
        assertThat(r.puntos()).isEqualTo(5);
    }

    @Test
    @DisplayName("+10 min 1 seg -> ya en EXTENDIDO, 3 puntos fijos")
    void extendidoJustoDespuesDeGracia() {
        var r = ResultadoOtorgamiento.calcular(ANCLA, ANCLA.plus(Duration.ofMinutes(10).plusSeconds(1)), EXTENSION);
        assertThat(r.fase()).isEqualTo(FaseOtorgamiento.EXTENDIDO);
        assertThat(r.puntos()).isEqualTo(3);
    }

    @Test
    @DisplayName("dentro de la extension completa (gracia+3h) -> EXTENDIDO, 3 puntos fijos sin importar cuanto de la ventana use")
    void extendidoAlBordeDeLaVentana() {
        Instant borde = ANCLA.plus(Duration.ofMinutes(10)).plus(EXTENSION);
        var r = ResultadoOtorgamiento.calcular(ANCLA, borde, EXTENSION);
        assertThat(r.fase()).isEqualTo(FaseOtorgamiento.EXTENDIDO);
        assertThat(r.puntos()).isEqualTo(3);
    }

    @Test
    @DisplayName("pasado gracia+extension -> EXPIRADO, 0 puntos")
    void expiradoPasadoTodoElPlazo() {
        Instant pasado = ANCLA.plus(Duration.ofMinutes(10)).plus(EXTENSION).plusSeconds(1);
        var r = ResultadoOtorgamiento.calcular(ANCLA, pasado, EXTENSION);
        assertThat(r.fase()).isEqualTo(FaseOtorgamiento.EXPIRADO);
        assertThat(r.puntos()).isEqualTo(0);
    }

    @Test
    @DisplayName("extension en 0 (habito 'sin margen'): pasada la gracia se cae directo a EXPIRADO")
    void extensionCeroSaltaDirectoAExpirado() {
        var r = ResultadoOtorgamiento.calcular(ANCLA, ANCLA.plus(Duration.ofMinutes(11)), Duration.ZERO);
        assertThat(r.fase()).isEqualTo(FaseOtorgamiento.EXPIRADO);
    }
}
