package com.renaser.os.rocks.domain.model.rocamensual;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El tramo de la semana, derivado del mes.
 *
 * <p>El ejemplo del dueno, textual: <i>"en los 90 dias bajare 6 kg, entonces por mes bajo 2 kg, y
 * luego por semana bajare 0,5 kg"</i>. Los tests de abajo lo siguen con sus numeros, y explican la
 * unica diferencia: el mes 1 no pide 2 kg sino 2,4, porque los meses siguen la curva 40/75/100 del
 * manual del cliente y no tercios (D-147). Repartido en las semanas que quedan da 0,48 — que es el
 * "0,5 digamos" del ejemplo.
 */
class ObjetivoDeLaSemanaTest {

    private static final BigDecimal OCHENTA_Y_CUATRO = new BigDecimal("84");
    private static final BigDecimal SETENTA_Y_OCHO = new BigDecimal("78");

    private static ObjetivoDeLaSemana semanaDePeso(int diaPrograma, BigDecimal hoy) {
        int mes = MesPrograma.deDia(diaPrograma);
        ObjetivoDelMes delMes = CalculadoraObjetivoMensual.calcular(mes, mes, OCHENTA_Y_CUATRO, hoy,
                SETENTA_Y_OCHO, Magnitud.NIVEL, null);
        return ObjetivoDeLaSemana.desde(delMes, hoy, diaPrograma, Magnitud.NIVEL);
    }

    @Test
    @DisplayName("el ejemplo del dueno: 6 kg en 90 dias, el dia 1")
    void elEjemploDelDueno() {
        ObjetivoDeLaSemana semana = semanaDePeso(1, OCHENTA_Y_CUATRO);

        // Mes 1 cierra en 81,6 (2,4 kg). Quedan las 5 semanas del mes, asi que 2,4 / 5 = 0,48.
        assertThat(semana.semanasQueQuedan()).isEqualTo(5);
        assertThat(semana.paso()).isEqualByComparingTo("0.48");
        // 83,52 se muestra como 83,5: de 10 para arriba va un decimal, como los hitos del Mapa.
        assertThat(semana.valor()).isEqualByComparingTo("83.5");
        assertThat(semana.sube()).isFalse();
    }

    @Test
    @DisplayName("se recalcula contra el valor real: una semana perdida sube la cuota de la siguiente")
    void unaSemanaPerdidaSubeLaSiguiente() {
        ObjetivoDeLaSemana alDia = semanaDePeso(8, new BigDecimal("83.52"));
        ObjetivoDeLaSemana atrasado = semanaDePeso(8, OCHENTA_Y_CUATRO);

        assertThat(atrasado.paso()).isGreaterThan(alDia.paso());
    }

    @Test
    @DisplayName("la ultima semana del mes pide todo lo que falta del mes")
    void laUltimaSemanaPideTodo() {
        ObjetivoDeLaSemana semana = semanaDePeso(30, new BigDecimal("82"));

        assertThat(semana.semanasQueQuedan()).isEqualTo(1);
        assertThat(semana.valor()).isEqualByComparingTo("81.6");
    }

    @Test
    @DisplayName("sin cifra en el mes no hay cifra en la semana, y no se inventa un motivo nuevo")
    void sinMesNoHaySemana() {
        ObjetivoDelMes sinTipo = CalculadoraObjetivoMensual.calcular(1, 1, OCHENTA_Y_CUATRO, null,
                SETENTA_Y_OCHO, null, null);

        assertThat(ObjetivoDeLaSemana.desde(sinTipo, OCHENTA_Y_CUATRO, 1, null)).isNull();
    }

    @Test
    @DisplayName("con la meta ya alcanzada tampoco hay tramo que pedir")
    void yaAlcanzadoNoPideNada() {
        ObjetivoDelMes alcanzado = CalculadoraObjetivoMensual.calcular(1, 1, OCHENTA_Y_CUATRO,
                new BigDecimal("77"), SETENTA_Y_OCHO, Magnitud.NIVEL, null);

        assertThat(ObjetivoDeLaSemana.desde(alcanzado, new BigDecimal("77"), 1, Magnitud.NIVEL)).isNull();
    }

    @Test
    @DisplayName("no solo peso: una meta de negocio que SUBE se reparte igual")
    void tambienSirveParaNegocio() {
        ObjetivoDelMes delMes = CalculadoraObjetivoMensual.calcular(1, 1, new BigDecimal("5000"), null,
                new BigDecimal("15000"), Magnitud.NIVEL, null);
        ObjetivoDeLaSemana semana = ObjetivoDeLaSemana.desde(delMes, new BigDecimal("5000"), 1, Magnitud.NIVEL);

        // Mes 1 cierra en 9 000 (4 000 de subida). En 5 semanas, 800 por semana.
        assertThat(semana.sube()).isTrue();
        assertThat(semana.paso()).isEqualByComparingTo("800");
        assertThat(semana.valor()).isEqualByComparingTo("5800");
    }

    @Test
    @DisplayName("una escala del 1 al 10 se reparte entera, como el mes")
    void laEscalaVaEntera() {
        ObjetivoDelMes delMes = CalculadoraObjetivoMensual.calcular(1, 1, new BigDecimal("4"), null,
                new BigDecimal("8"), Magnitud.ESCALA, null);
        ObjetivoDeLaSemana semana = ObjetivoDeLaSemana.desde(delMes, new BigDecimal("4"), 1, Magnitud.ESCALA);

        assertThat(semana.valor().scale()).isLessThanOrEqualTo(0);
    }

    @Test
    @DisplayName("las semanas que quedan salen del dia DENTRO del mes, no del dia de programa")
    void semanasPorMes() {
        assertThat(MesPrograma.semanasQueQuedanDelMes(1)).isEqualTo(5);
        assertThat(MesPrograma.semanasQueQuedanDelMes(30)).isEqualTo(1);
        // Dia 31 es el dia 1 del mes 2: vuelve a haber cinco semanas por delante.
        assertThat(MesPrograma.semanasQueQuedanDelMes(31)).isEqualTo(5);
        assertThat(MesPrograma.diaDentroDelMes(31)).isEqualTo(1);
        assertThat(MesPrograma.diaDentroDelMes(60)).isEqualTo(30);
    }
}
