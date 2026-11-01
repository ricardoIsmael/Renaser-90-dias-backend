package com.renaser.os.calendar.domain.model.nivelmembresia;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Puerto directo de programProgressPercent()/resolveLevelRank() (audience.ts, repo viejo). */
class ProgresoNivelTest {

    @Test
    void porcentajeSeRedondeaYTopaEnCien() {
        assertThat(ProgresoNivel.porcentajeDeProgreso(45, 90)).isEqualTo(50); // 45/90 = 50%
        assertThat(ProgresoNivel.porcentajeDeProgreso(90, 90)).isEqualTo(100);
        assertThat(ProgresoNivel.porcentajeDeProgreso(120, 90)).isEqualTo(100); // post-programa, topa en 100
        assertThat(ProgresoNivel.porcentajeDeProgreso(0, 90)).isEqualTo(0);
    }

    @Test
    void elPorcentajeSeCuentaSobreLaDuracionDeLaFormacion() {
        // Diplomado de 1 año: el día 45 es el 12 %, no el 50 % que daría dividir por 90.
        assertThat(ProgresoNivel.porcentajeDeProgreso(45, 365)).isEqualTo(12);
        assertThat(ProgresoNivel.porcentajeDeProgreso(365, 365)).isEqualTo(100);
        assertThat(ProgresoNivel.porcentajeDeProgreso(400, 365)).isEqualTo(100);
        // Una duración rota no divide por cero: cuenta como la de siempre.
        assertThat(ProgresoNivel.porcentajeDeProgreso(45, 0)).isEqualTo(50);
    }

    @Test
    void sinNivelesElRangoEsCero() {
        assertThat(ProgresoNivel.resolverRango(80, List.of())).isEqualTo(0);
    }

    @Test
    void resuelveElRangoMasAltoQueCalifica() {
        List<NivelMembresia> niveles = List.of(
                new NivelMembresia(1, 1, "Bronce", 0),
                new NivelMembresia(2, 2, "Plata", 30),
                new NivelMembresia(3, 3, "Oro", 70));

        assertThat(ProgresoNivel.resolverRango(0, niveles)).isEqualTo(1);
        assertThat(ProgresoNivel.resolverRango(50, niveles)).isEqualTo(2);
        assertThat(ProgresoNivel.resolverRango(100, niveles)).isEqualTo(3);
    }
}
