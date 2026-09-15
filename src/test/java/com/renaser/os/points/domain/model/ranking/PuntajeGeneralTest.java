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
                new BigDecimal("40.0")).orElseThrow();

        // 0.5*80 + 0.35*60 + 0.15*40 = 40 + 21 + 6 = 67.0
        assertThat(puntaje).isEqualByComparingTo("67.0");
    }

    @Test
    @DisplayName("conserva el decimal de cada componente, no lo trunca antes de ponderar")
    void conservaElDecimalDeLosComponentes() {
        // Si los componentes se hubieran redondeado a entero antes (33 / 67 / 89),
        // el resultado seria 0.5*33 + 0.35*67 + 0.15*89 = 16.5 + 23.45 + 13.35 = 53.3
        BigDecimal puntaje = PuntajeGeneral.calcular(new BigDecimal("33.3"), new BigDecimal("66.7"),
                new BigDecimal("88.9")).orElseThrow();

        // 16.65 + 23.345 + 13.335 = 53.33 -> 53.3
        assertThat(puntaje).isEqualByComparingTo("53.3");
    }

    @Test
    @DisplayName("redondea el resultado final a un decimal, como round(x*10)/10 en el SQL")
    void redondeaAUnDecimal() {
        BigDecimal puntaje = PuntajeGeneral.calcular(new BigDecimal("77.7"), new BigDecimal("77.7"),
                new BigDecimal("77.7")).orElseThrow();

        assertThat(puntaje.scale()).isEqualTo(1);
        assertThat(puntaje).isEqualByComparingTo("77.7");
    }

    /**
     * D-131. Se llamaba {@code sinDatoVale100} y esperaba <b>100.0</b> con los tres componentes
     * vacios — el porte literal del {@code COALESCE(pct, 100)} del backend viejo. En una tabla
     * ORDENADA ese 100 ponia PRIMERO a quien no tenia un solo dato.
     */
    @Test
    @DisplayName("sin ningun dato no hay puntaje: ni 100 ni 0, no hay nada que promediar")
    void sinNingunDatoNoHayPuntaje() {
        assertThat(PuntajeGeneral.calcular(null, null, null)).isEmpty();
    }

    /**
     * La propiedad que protege lo que ya funciona: con los tres modulos presentes, renormalizar da
     * EXACTAMENTE el mismo numero que la ponderacion vieja —los pesos suman 1—, asi que quien ya
     * venia compitiendo no se mueve de lugar.
     */
    @Test
    @DisplayName("con los tres componentes da el mismo numero que la formula vieja")
    void conLosTresComponentesElNumeroNoCambia() {
        assertThat(PuntajeGeneral.calcular(new BigDecimal("80.0"), new BigDecimal("60.0"),
                new BigDecimal("40.0")).orElseThrow()).isEqualByComparingTo("67.0");
        assertThat(PuntajeGeneral.calcular(new BigDecimal("0.0"), new BigDecimal("0.0"),
                new BigDecimal("0.0")).orElseThrow()).isEqualByComparingTo("0.0");
    }

    /**
     * D-131. Antes daba <b>67.5</b>: las rocas ausentes entraban como 100 y le regalaban 35 puntos
     * de ponderacion. Ahora salen del promedio y queda 50 de habitos y 50 de cursos renormalizados
     * sobre 0.65 de peso = <b>50.0</b>. Ni premio ni castigo por lo que todavia no existe.
     */
    @Test
    @DisplayName("un componente sin dato sale del promedio: ni suma ni resta")
    void unComponenteSinDatoSaleDelPromedio() {
        BigDecimal puntaje = PuntajeGeneral.calcular(new BigDecimal("50.0"), null, new BigDecimal("50.0"))
                .orElseThrow();

        assertThat(puntaje).isEqualByComparingTo("50.0");
    }

    @Test
    @DisplayName("todo completo da 100")
    void todoCompletoDaCien() {
        BigDecimal puntaje = PuntajeGeneral.calcular(new BigDecimal("100.0"), new BigDecimal("100.0"),
                new BigDecimal("100.0")).orElseThrow();

        assertThat(puntaje).isEqualByComparingTo("100.0");
    }
}
