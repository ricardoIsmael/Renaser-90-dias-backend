package com.renaser.os.habits.domain.model.registro;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Traduccion literal de {@code averageCompletionForDates}
 * (coherence.ts:114-131, repo viejo) — ver docs/MODULO_HABITS.md §9 paso 0
 * para las citas archivo:linea completas.
 */
class PorcentajeHabitosTest {

    private static final LocalDate DIA_1 = LocalDate.of(2026, 8, 18);
    private static final LocalDate DIA_2 = LocalDate.of(2026, 8, 19);

    @Test
    @DisplayName("ventana sin ningun dia calificable -> 100 (coherence.ts:127, 'recien empezo')")
    void ventanaVacia() {
        PorcentajeHabitos resultado = PorcentajeHabitos.calcular(List.of());

        assertThat(resultado.valor()).isEqualByComparingTo(new BigDecimal("100.0"));
    }

    @Test
    @DisplayName("un dia con habitos, 100% completado -> 100.0")
    void unSoloDiaPerfecto() {
        var conteo = new ConteoDiarioHabitos(DIA_1, 4, 4, 0);

        PorcentajeHabitos resultado = PorcentajeHabitos.calcular(List.of(conteo));

        assertThat(resultado.valor()).isEqualByComparingTo(new BigDecimal("100.0"));
    }

    @Test
    @DisplayName("habito opcional sin completar no entra ni al numerador ni al denominador (coherence.ts:61-68)")
    void opcionalSinCompletarNoCastiga() {
        // 5 registros ese dia: 3 obligatorios completados + 2 opcionales SIN completar.
        // calificables = 5 - 2 = 3; completados = 3 -> 100%, los 2 opcionales no bajan el score.
        var conteo = new ConteoDiarioHabitos(DIA_1, 5, 3, 2);

        PorcentajeHabitos resultado = PorcentajeHabitos.calcular(List.of(conteo));

        assertThat(resultado.valor()).isEqualByComparingTo(new BigDecimal("100.0"));
    }

    @Test
    @DisplayName("dia sin nada calificable (todo opcional sin completar) se excluye de la ventana, no cuenta como 0%")
    void diaSinCalificablesSeExcluyeDeLaVentana() {
        // calificables = 2 - 2 = 0 -> el dia no entra al promedio; sin otros dias, ventana "vacia" -> 100.
        var conteo = new ConteoDiarioHabitos(DIA_1, 2, 0, 2);

        PorcentajeHabitos resultado = PorcentajeHabitos.calcular(List.of(conteo));

        assertThat(resultado.valor()).isEqualByComparingTo(new BigDecimal("100.0"));
    }

    @Test
    @DisplayName("redondeo de dia exacto a mitad (37.5%) redondea hacia arriba, igual que Math.round de JS")
    void redondeoDeDiaMitadExacta() {
        // 3/8 = 37.5% exacto -> Math.round(37.5) = 38 en JS y en Java (round-half-up, positivos)
        var conteo = new ConteoDiarioHabitos(DIA_1, 8, 3, 0);

        PorcentajeHabitos resultado = PorcentajeHabitos.calcular(List.of(conteo));

        assertThat(resultado.valor()).isEqualByComparingTo(new BigDecimal("38.0"));
    }

    @Test
    @DisplayName("doble redondeo deliberado: redondear cada dia ANTES de promediar da un resultado distinto "
            + "que promediar las fracciones crudas y redondear una sola vez al final")
    void dobleRedondeoDaResultadoDistintoDeUnSoloRedondeo() {
        // dia 1: 2/3 = 66.666...% -> redondeado a 67
        // dia 2: 1/6 = 16.666...% -> redondeado a 17
        // promedio de los PUNTAJES YA REDONDEADOS: (67 + 17) / 2 = 42.0
        //
        // Si en cambio se promediaran las fracciones crudas antes de redondear una sola vez
        // ((2/3 + 1/6) / 2 * 100 = 41.6666... -> 41.7), el resultado seria distinto (41.7 != 42.0) —
        // confirma que el doble redondeo de coherence.ts:97+130 es necesario para reproducir el
        // numero exacto del sistema viejo, no un detalle de implementacion sin efecto observable.
        var dia1 = new ConteoDiarioHabitos(DIA_1, 3, 2, 0);
        var dia2 = new ConteoDiarioHabitos(DIA_2, 6, 1, 0);

        PorcentajeHabitos resultado = PorcentajeHabitos.calcular(List.of(dia1, dia2));

        assertThat(resultado.valor()).isEqualByComparingTo(new BigDecimal("42.0"));
    }

    @Test
    @DisplayName("el promedio final se redondea a 1 decimal, no a entero (coherence.ts:130)")
    void promedioFinalConUnDecimal() {
        // dia 1: 1/3 -> round(33.33) = 33
        // dia 2: 2/3 -> round(66.67) = 67
        // dia 3: 1/1 -> 100
        // avg(33, 67, 100) = 200/3 = 66.6666... -> round(66.6666*10)/10 = 66.7
        var dia1 = new ConteoDiarioHabitos(DIA_1, 3, 1, 0);
        var dia2 = new ConteoDiarioHabitos(DIA_2, 3, 2, 0);
        var dia3 = new ConteoDiarioHabitos(LocalDate.of(2026, 8, 20), 1, 1, 0);

        PorcentajeHabitos resultado = PorcentajeHabitos.calcular(List.of(dia1, dia2, dia3));

        assertThat(resultado.valor()).isEqualByComparingTo(new BigDecimal("66.7"));
    }

    @Test
    @DisplayName("promedio de dias sin decimal significativo (100, 100, 67 -> 89.0)")
    void promedioSinDecimalSignificativo() {
        var dia1 = new ConteoDiarioHabitos(LocalDate.of(2026, 8, 18), 1, 1, 0);
        var dia2 = new ConteoDiarioHabitos(LocalDate.of(2026, 8, 19), 1, 1, 0);
        var dia3 = new ConteoDiarioHabitos(LocalDate.of(2026, 8, 20), 3, 2, 0); // round(2/3*100) = 67

        PorcentajeHabitos resultado = PorcentajeHabitos.calcular(List.of(dia1, dia2, dia3));

        assertThat(resultado.valor()).isEqualByComparingTo(new BigDecimal("89.0"));
    }

    @Test
    @DisplayName("promedio con decimal .3 -- Integer lo hubiera perdido en silencio (D-43, correccion del coordinador)")
    void promedioConDecimalPuntoTres() {
        // puntajes diarios: 0, 50, 50 -> avg = 100/3 = 33.333... -> round(333.33)/10 = 33.3
        var dia1 = new ConteoDiarioHabitos(LocalDate.of(2026, 8, 18), 1, 0, 0);
        var dia2 = new ConteoDiarioHabitos(LocalDate.of(2026, 8, 19), 2, 1, 0);
        var dia3 = new ConteoDiarioHabitos(LocalDate.of(2026, 8, 20), 2, 1, 0);

        PorcentajeHabitos resultado = PorcentajeHabitos.calcular(List.of(dia1, dia2, dia3));

        assertThat(resultado.valor()).isEqualByComparingTo(new BigDecimal("33.3"));
    }
}
