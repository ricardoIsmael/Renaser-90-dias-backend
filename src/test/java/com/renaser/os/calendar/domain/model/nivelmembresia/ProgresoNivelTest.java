package com.renaser.os.calendar.domain.model.nivelmembresia;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Puerto directo de programProgressPercent()/resolveLevelRank() (audience.ts, repo viejo). */
class ProgresoNivelTest {

    @Test
    void porcentajeSeRedondeaYTopaEnCien() {
        assertThat(ProgresoNivel.porcentajeDeProgreso(45)).isEqualTo(50); // 45/90 = 50%
        assertThat(ProgresoNivel.porcentajeDeProgreso(90)).isEqualTo(100);
        assertThat(ProgresoNivel.porcentajeDeProgreso(120)).isEqualTo(100); // post-programa, topa en 100
        assertThat(ProgresoNivel.porcentajeDeProgreso(0)).isEqualTo(0);
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
