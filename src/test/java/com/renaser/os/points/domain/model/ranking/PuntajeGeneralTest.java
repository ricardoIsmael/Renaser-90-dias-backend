package com.renaser.os.points.domain.model.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La formula del ranking general, verificada contra `general_ranking_scores()` del repo
 * viejo. Sin Spring y sin Postgres: ese es justamente el motivo de D-43 (la formula no
 * vive en un procedimiento almacenado).
 */
class PuntajeGeneralTest {

    @Test
    @DisplayName("pondera 50% habitos, 35% rocas y 15% cursos")
    void ponderaLosTresComponentes() {
        BigDecimal puntaje = PuntajeGeneral.calcular(new BigDecimal("80.0"), new BigDecimal("60.0"),
                new BigDecimal("40.0"));

        // 0.5*80 + 0.35*60 + 0.15*40 = 40 + 21 + 6 = 67.0
        assertThat(puntaje).isEqualByComparingTo("67.0");
    }

    @Test
    @DisplayName("conserva el decimal de cada componente, no lo trunca antes de ponderar")
    void conservaElDecimalDeLosComponentes() {
        // Si los componentes se hubieran redondeado a entero antes (33 / 67 / 89),
        // el resultado seria 0.5*33 + 0.35*67 + 0.15*89 = 16.5 + 23.45 + 13.35 = 53.3
        BigDecimal puntaje = PuntajeGeneral.calcular(new BigDecimal("33.3"), new BigDecimal("66.7"),
                new BigDecimal("88.9"));

        // 16.65 + 23.345 + 13.335 = 53.33 -> 53.3
        assertThat(puntaje).isEqualByComparingTo("53.3");
    }

    @Test
    @DisplayName("redondea el resultado final a un decimal, como round(x*10)/10 en el SQL")
    void redondeaAUnDecimal() {
        BigDecimal puntaje = PuntajeGeneral.calcular(new BigDecimal("77.7"), new BigDecimal("77.7"),
                new BigDecimal("77.7"));

        assertThat(puntaje.scale()).isEqualTo(1);
        assertThat(puntaje).isEqualByComparingTo("77.7");
    }

    @Test
    @DisplayName("sin dato vale 100, no 0: al que recien empieza no se lo castiga")
    void sinDatoVale100() {
        assertThat(PuntajeGeneral.calcular(null, null, null)).isEqualByComparingTo("100.0");
    }

    @Test
    @DisplayName("un componente sin dato no arrastra el resto a cero")
    void unComponenteSinDatoNoHundeElPuntaje() {
        // Sin rocas: 0.5*50 + 0.35*100 + 0.15*50 = 25 + 35 + 7.5 = 67.5
        BigDecimal puntaje = PuntajeGeneral.calcular(new BigDecimal("50.0"), null, new BigDecimal("50.0"));

        assertThat(puntaje).isEqualByComparingTo("67.5");
    }

    @Test
    @DisplayName("todo completo da 100")
    void todoCompletoDaCien() {
        BigDecimal puntaje = PuntajeGeneral.calcular(new BigDecimal("100.0"), new BigDecimal("100.0"),
                new BigDecimal("100.0"));

        assertThat(puntaje).isEqualByComparingTo("100.0");
    }
}
