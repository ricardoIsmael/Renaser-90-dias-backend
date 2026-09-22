package com.renaser.os.points.domain.model.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La formula del ranking general. Sin Spring y sin Postgres: ese es justamente el motivo de D-43
 * (la formula no vive en un procedimiento almacenado).
 *
 * <blockquote><b>Corregido el 2026-09-22.</b> Todos los casos de aca pasaban TRES componentes
 * (habitos, rocas, cursos) y esperaban la ponderacion 50/35/15. Las rocas salieron del ranking
 * general —son los OBJETIVOS, y su porcentaje ES la coherencia, que se muestra y se ordena
 * aparte—, asi que quedan dos componentes: <b>0.75 habitos y 0.25 cursos</b>, que suman 1.
 * </blockquote>
 */
class PuntajeGeneralTest {

    @Test
    @DisplayName("pondera habitos y cursos, sin rocas")
    void ponderaLosDosComponentes() {
        BigDecimal puntaje = PuntajeGeneral.calcular(new BigDecimal("80.0"), new BigDecimal("40.0")).orElseThrow();

        // 0.75*80 + 0.25*40 = 60 + 10 = 70.0 (los pesos suman 1: no hay renormalizacion de por medio)
        assertThat(puntaje).isEqualByComparingTo("70.0");
    }

    /**
     * El test que fija los pesos: 75 y 25. Si alguien toca cualquiera de las dos constantes de
     * {@link PuntajeGeneral}, estos dos numeros cambian y el test lo dice en la cara — cambiarlos
     * reordena la tabla para todo el padron, asi que no puede pasar inadvertido.
     */
    @Test
    @DisplayName("el peso es 75% habitos / 25% cursos, y suman 100")
    void elPesoEsSetentaYCincoVeinticinco() {
        assertThat(PuntajeGeneral.calcular(new BigDecimal("100.0"), new BigDecimal("0.0")).orElseThrow())
                .isEqualByComparingTo("75.0");
        assertThat(PuntajeGeneral.calcular(new BigDecimal("0.0"), new BigDecimal("100.0")).orElseThrow())
                .isEqualByComparingTo("25.0");
    }

    @Test
    @DisplayName("conserva el decimal de cada componente, no lo trunca antes de ponderar")
    void conservaElDecimalDeLosComponentes() {
        // Si los componentes se hubieran redondeado a entero antes (33 / 89), el resultado seria
        // 0.75*33 + 0.25*89 = 24.75 + 22.25 = 47.0
        BigDecimal puntaje = PuntajeGeneral.calcular(new BigDecimal("33.3"), new BigDecimal("88.9")).orElseThrow();

        // 24.975 + 22.225 = 47.2
        assertThat(puntaje).isEqualByComparingTo("47.2");
    }

    @Test
    @DisplayName("redondea el resultado final a un decimal")
    void redondeaAUnDecimal() {
        BigDecimal puntaje = PuntajeGeneral.calcular(new BigDecimal("77.7"), new BigDecimal("77.7")).orElseThrow();

        assertThat(puntaje.scale()).isEqualTo(1);
        assertThat(puntaje).isEqualByComparingTo("77.7");
    }

    /**
     * D-131. Se llamaba {@code sinDatoVale100} y esperaba <b>100.0</b> con los componentes vacios —
     * el porte literal del {@code COALESCE(pct, 100)} del backend viejo. En una tabla ORDENADA ese
     * 100 ponia PRIMERO a quien no tenia un solo dato.
     */
    @Test
    @DisplayName("sin ningun dato no hay puntaje: ni 100 ni 0, no hay nada que promediar")
    void sinNingunDatoNoHayPuntaje() {
        assertThat(PuntajeGeneral.calcular(null, null)).isEmpty();
    }

    /**
     * D-131. Con un solo modulo con dato, renormalizar devuelve ese mismo porcentaje: ni premio ni
     * castigo por lo que todavia no existe. Vale para los dos lados.
     */
    @Test
    @DisplayName("un componente sin dato sale del promedio: ni suma ni resta")
    void unComponenteSinDatoSaleDelPromedio() {
        assertThat(PuntajeGeneral.calcular(new BigDecimal("50.0"), null).orElseThrow())
                .isEqualByComparingTo("50.0");
        assertThat(PuntajeGeneral.calcular(null, new BigDecimal("50.0")).orElseThrow())
                .isEqualByComparingTo("50.0");
    }

    @Test
    @DisplayName("todo completo da 100, y todo en cero da 0")
    void losExtremosSeMantienen() {
        assertThat(PuntajeGeneral.calcular(new BigDecimal("100.0"), new BigDecimal("100.0")).orElseThrow())
                .isEqualByComparingTo("100.0");
        assertThat(PuntajeGeneral.calcular(new BigDecimal("0.0"), new BigDecimal("0.0")).orElseThrow())
                .isEqualByComparingTo("0.0");
    }
}
