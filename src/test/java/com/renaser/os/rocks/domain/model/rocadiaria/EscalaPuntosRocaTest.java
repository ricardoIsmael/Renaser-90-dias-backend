package com.renaser.os.rocks.domain.model.rocadiaria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EscalaPuntosRocaTest {

    private static final Instant HORA_FIN = Instant.parse("2026-08-24T20:00:00Z");

    @Test
    @DisplayName("sin horaFin, siempre 10 puntos a tiempo")
    void sinHoraFinSiemprePuntosCompletos() {
        var resultado = EscalaPuntosRoca.calcular(null, Instant.parse("2026-08-24T23:00:00Z"));
        assertThat(resultado.fase()).isEqualTo(FasePremio.A_TIEMPO);
        assertThat(resultado.puntos()).isEqualTo(10);
    }

    @Test
    @DisplayName("completada exactamente a horaFin: 10 puntos")
    void completadaJustoAHoraFin() {
        var resultado = EscalaPuntosRoca.calcular(HORA_FIN, HORA_FIN);
        assertThat(resultado.fase()).isEqualTo(FasePremio.A_TIEMPO);
        assertThat(resultado.puntos()).isEqualTo(10);
    }

    @Test
    @DisplayName("completada antes de horaFin: 10 puntos")
    void completadaAntesDeHoraFin() {
        var resultado = EscalaPuntosRoca.calcular(HORA_FIN, HORA_FIN.minusSeconds(600));
        assertThat(resultado.puntos()).isEqualTo(10);
    }

    @Test
    @DisplayName("1 minuto tarde: todavia 10 (floor(1/2)=0)")
    void unMinutoTardeSinPenalizacion() {
        var resultado = EscalaPuntosRoca.calcular(HORA_FIN, HORA_FIN.plusSeconds(60));
        assertThat(resultado.fase()).isEqualTo(FasePremio.GRACIA);
        assertThat(resultado.puntos()).isEqualTo(10);
    }

    @Test
    @DisplayName("2 minutos tarde: -1 punto")
    void dosMinutosTarde() {
        var resultado = EscalaPuntosRoca.calcular(HORA_FIN, HORA_FIN.plusSeconds(120));
        assertThat(resultado.puntos()).isEqualTo(9);
    }

    @Test
    @DisplayName("exactamente 10 minutos tarde: piso de gracia, 5 puntos")
    void diezMinutosTardeEsElPisoDeGracia() {
        var resultado = EscalaPuntosRoca.calcular(HORA_FIN, HORA_FIN.plusSeconds(600));
        assertThat(resultado.fase()).isEqualTo(FasePremio.GRACIA);
        assertThat(resultado.puntos()).isEqualTo(5);
    }

    @Test
    @DisplayName("11 minutos tarde: ya en extension, 3 puntos fijos")
    void onceMinutosTardeEntraEnExtension() {
        var resultado = EscalaPuntosRoca.calcular(HORA_FIN, HORA_FIN.plusSeconds(660));
        assertThat(resultado.fase()).isEqualTo(FasePremio.EXTENDIDO);
        assertThat(resultado.puntos()).isEqualTo(3);
    }

    @Test
    @DisplayName("justo al borde de la extension (10 min gracia + 3h): todavia 3 puntos")
    void bordeDeLaExtension() {
        Instant limite = HORA_FIN.plusSeconds(600).plusSeconds(3 * 3600);
        var resultado = EscalaPuntosRoca.calcular(HORA_FIN, limite);
        assertThat(resultado.fase()).isEqualTo(FasePremio.EXTENDIDO);
        assertThat(resultado.puntos()).isEqualTo(3);
    }

    @Test
    @DisplayName("pasada la extension: EXPIRADO, 0 puntos")
    void pasadaLaExtensionExpira() {
        Instant pasado = HORA_FIN.plusSeconds(600).plusSeconds(3 * 3600).plusSeconds(1);
        var resultado = EscalaPuntosRoca.calcular(HORA_FIN, pasado);
        assertThat(resultado.fase()).isEqualTo(FasePremio.EXPIRADO);
        assertThat(resultado.puntos()).isEqualTo(0);
    }
}
