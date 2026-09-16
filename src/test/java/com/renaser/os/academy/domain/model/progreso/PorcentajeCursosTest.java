package com.renaser.os.academy.domain.model.progreso;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Espejo de {@code sumarProgresoCursos} (RenaserBack,
 * `src/features/cursos/repository.ts:824-849`) y, bit a bit, del CTE
 * {@code cursos_pct} de `prisma/migrations/general_ranking_scores_function.sql`
 * (RenaserBack): {@code round(completadas/total*1000)/10}, escala 1.
 */
class PorcentajeCursosTest {

    @Test
    @DisplayName("sin cursos accesibles (total = 0) -> sin dato: ni se castiga ni se premia con un 100")
    void sinCursosAccesiblesEsSinDato() {
        // Hasta el 2026-09-16 valia 100.0. Junto con el 100 de habitos, ponia al dia 0 primero.
        assertThat(PorcentajeCursos.calcular(0, 0)).isEmpty();
    }

    @Test
    @DisplayName("nada completado -> 0.0")
    void nadaCompletadoDaCero() {
        assertThat(PorcentajeCursos.calcular(10, 0).orElseThrow()).isEqualByComparingTo("0.0");
    }

    @Test
    @DisplayName("todo completado -> 100.0")
    void todoCompletadoDaCien() {
        assertThat(PorcentajeCursos.calcular(10, 10).orElseThrow()).isEqualByComparingTo("100.0");
    }

    @Test
    @DisplayName("1 de 3 lecciones -> 33.3, decimal significativo que un Integer perderia")
    void unDeTresDaTreintaYTresConDecimal() {
        assertThat(PorcentajeCursos.calcular(3, 1).orElseThrow()).isEqualByComparingTo("33.3");
    }

    @Test
    @DisplayName("2 de 3 lecciones -> 66.7 (round HALF_UP de 666.66... -> 667)")
    void dosDeTresDaSesentaYSeisConDecimal() {
        assertThat(PorcentajeCursos.calcular(3, 2).orElseThrow()).isEqualByComparingTo("66.7");
    }

    @Test
    @DisplayName("division exacta con un decimal -> escala 1 igual (5 de 8 -> 62.5)")
    void divisionExactaConUnDecimal() {
        assertThat(PorcentajeCursos.calcular(8, 5).orElseThrow()).isEqualByComparingTo("62.5");
    }

    @Test
    @DisplayName("el resultado siempre viaja con escala 1, incluso en valores enteros")
    void resultadoSiempreConEscalaUno() {
        BigDecimal resultado = PorcentajeCursos.calcular(4, 2).orElseThrow();

        assertThat(resultado.scale()).isEqualTo(1);
        assertThat(resultado).isEqualByComparingTo("50.0");
    }
}
