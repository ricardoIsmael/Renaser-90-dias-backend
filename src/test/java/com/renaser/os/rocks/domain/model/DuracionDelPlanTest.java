package com.renaser.os.rocks.domain.model;

import com.renaser.os.rocks.domain.model.rocamensual.CalculadoraObjetivoMensual;
import com.renaser.os.rocks.domain.model.rocamensual.CurvaDelPlan;
import com.renaser.os.rocks.domain.model.rocamensual.Magnitud;
import com.renaser.os.rocks.domain.model.rocamensual.MesPrograma;
import com.renaser.os.rocks.domain.model.rocamensual.ObjetivoDeLaSemana;
import com.renaser.os.rocks.domain.model.rocamensual.ObjetivoDelMes;
import com.renaser.os.rocks.domain.model.rocamensual.ObjetivoDelMes.ConCifra;
import com.renaser.os.rocks.domain.model.rocasemanal.SemanaPrograma;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 026 (D-303): el plan de objetivos lee la duracion del programa de CADA persona (90 dias, 3 meses del Diplomado; o 365, 1 ano) y no un 90 escrito de
 * antemano. Lo que fija esta prueba es el tope REAL: cuantas semanas y meses caben, para que los CHECK de la base (V156) cubran el maximo.
 */
class DuracionDelPlanTest {

    private static final LocalDate UN_LUNES = LocalDate.of(2026, 9, 7);

    /** La cuenta de semanas SIN el tope: cuantos lunes pasaron entre la semana del dia 1 y el ultimo dia del programa. */
    private static int semanaSinTopeDelUltimoDia(LocalDate primerDia, int duracion) {
        LocalDate lunesUno = primerDia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return (int) (ChronoUnit.DAYS.between(lunesUno, primerDia.plusDays(duracion - 1L)) / 7) + 1;
    }

    @ParameterizedTest(name = "empezando un {0}")
    @ValueSource(strings = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"})
    @DisplayName("365 dias: la semana del ultimo dia es la 53 empiece el dia que empiece (nunca la 54), y la 53 termina el dia 365")
    void deUnAnoSonCincuentaYTresSemanas(String dia) {
        LocalDate inicio = UN_LUNES.plusDays(DayOfWeek.valueOf(dia).getValue() - 1L);
        SemanaPrograma semanas = SemanaPrograma.desde(inicio, 365);

        assertThat(semanaSinTopeDelUltimoDia(inicio, 365)).isEqualTo(53);
        assertThat(semanas.ultimaSemana()).isEqualTo(53).isEqualTo(SemanaPrograma.MAXIMA_SEMANA);
        assertThat(semanas.numeroSemanaParaFecha(semanas.finDelPrograma())).isEqualTo(53);
        assertThat(semanas.finDelPrograma()).isEqualTo(inicio.plusDays(364));
        assertThat(semanas.limites(53).fin()).isEqualTo(semanas.finDelPrograma());
        long diasDeLaUltima = ChronoUnit.DAYS.between(semanas.limites(53).inicio(), semanas.limites(53).fin()) + 1;
        assertThat(diasDeLaUltima).as("la ultima semana dura de 1 a 12 dias, como la 13 de los 90").isBetween(1L, 12L);
        assertThat(semanas.numeroSemanaParaFecha(inicio.plusDays(500))).as("pasado el ultimo dia sigue siendo la ultima").isEqualTo(53);
        assertThat(semanas.limites(54)).isEqualTo(semanas.limites(53));
    }

    @ParameterizedTest(name = "empezando un {0}")
    @ValueSource(strings = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"})
    @DisplayName("90 dias: sigue siendo la 13, que absorbe lo que caeria en una 14 (D-203)")
    void deNoventaSiguenSiendoTrece(String dia) {
        LocalDate inicio = UN_LUNES.plusDays(DayOfWeek.valueOf(dia).getValue() - 1L);
        SemanaPrograma semanas = SemanaPrograma.desde(inicio, 90);

        assertThat(semanas.ultimaSemana()).isEqualTo(13);
        assertThat(semanas.numeroSemanaParaFecha(semanas.finDelPrograma())).isEqualTo(13);
        assertThat(semanas.finDelPrograma()).isEqualTo(inicio.plusDays(89));
        assertThat(SemanaPrograma.desde(inicio).ultimaSemana()).isEqualTo(13);
    }

    @Test
    @DisplayName("para cualquier duracion de 1 a 365 y cualquier dia de arranque la ultima semana contiene el ultimo dia, nunca pasa del tope y su rango existe")
    void ningunaDuracionPasaDelMaximo() {
        for (int duracion = 1; duracion <= 365; duracion++) {
            for (int desfase = 0; desfase < 7; desfase++) {
                LocalDate inicio = UN_LUNES.plusDays(desfase);
                SemanaPrograma semanas = SemanaPrograma.desde(inicio, duracion);
                String caso = "duracion " + duracion + " desfase " + desfase;
                int sinTope = semanaSinTopeDelUltimoDia(inicio, duracion);

                assertThat(semanas.ultimaSemana()).as(caso).isEqualTo(Math.min(sinTope, SemanaPrograma.ultimaSemanaDe(duracion)));
                assertThat(semanas.ultimaSemana()).as(caso).isBetween(1, SemanaPrograma.MAXIMA_SEMANA);
                assertThat(sinTope).as(caso + ": nunca mas de una semana por encima del tope").isLessThanOrEqualTo(SemanaPrograma.ultimaSemanaDe(duracion) + 1);
                SemanaPrograma.LimitesSemana ultima = semanas.limites(semanas.ultimaSemana());
                assertThat(ultima.fin()).as(caso).isEqualTo(semanas.finDelPrograma());
                assertThat(ultima.inicio()).as(caso).isBeforeOrEqualTo(ultima.fin());
            }
        }
        assertThat(SemanaPrograma.MAXIMA_SEMANA).isEqualTo(53);
    }

    @Test
    @DisplayName("la semana a planificar el domingo de la penultima pasa a la ultima, y nunca la pasa")
    void planificarNoPasaDeLaUltima() {
        SemanaPrograma semanas = SemanaPrograma.desde(UN_LUNES, 365);
        LocalDate ultimoDomingo = semanas.limites(52).fin();

        assertThat(ultimoDomingo.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(semanas.semanaAPlanificar(ultimoDomingo)).isEqualTo(53);
        assertThat(semanas.semanaAPlanificar(semanas.finDelPrograma())).isLessThanOrEqualTo(53);
    }

    @Test
    @DisplayName("una duracion fuera de 1 a 365 se rechaza")
    void duracionInvalida() {
        assertThatThrownBy(() -> SemanaPrograma.desde(UN_LUNES, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SemanaPrograma.desde(UN_LUNES, 366)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("los meses son bloques de 30 dias y el ultimo absorbe el resto: 3 en 90 dias, 12 en 365 (el ultimo de 35)")
    void losMeses() {
        assertThat(MesPrograma.mesesDe(90)).isEqualTo(3);
        assertThat(MesPrograma.mesesDe(365)).isEqualTo(12).isEqualTo(MesPrograma.MAXIMO_DE_MESES);
        assertThat(MesPrograma.mesesDe(20)).as("un programa de menos de 30 dias tiene un mes").isEqualTo(1);
        assertThat(MesPrograma.ultimoDiaDe(3, 90)).isEqualTo(90);
        assertThat(MesPrograma.ultimoDiaDe(11, 365)).isEqualTo(330);
        assertThat(MesPrograma.ultimoDiaDe(12, 365)).as("el cierre del ultimo mes es el ultimo dia del programa").isEqualTo(365);
        assertThat(MesPrograma.ultimoDiaDe(40, 365)).isEqualTo(365);
        assertThat(MesPrograma.deDia(330, 365)).isEqualTo(11);
        assertThat(MesPrograma.deDia(331, 365)).isEqualTo(12);
        assertThat(MesPrograma.deDia(365, 365)).isEqualTo(12);
        assertThat(MesPrograma.deDia(95, 90)).as("pasado el dia 90 se queda en el 3").isEqualTo(3);
        assertThat(MesPrograma.deDia(0, 365)).isEqualTo(1);
        assertThat(MesPrograma.deDia(60, 90)).isEqualTo(MesPrograma.deDia(60));
    }

    @Test
    @DisplayName("las semanas que quedan del mes: igual que antes en 90 dias, y el ultimo mes de 35 dias tiene cinco bloques")
    void semanasQueQuedan() {
        for (int dia = 1; dia <= 90; dia++) {
            assertThat(MesPrograma.semanasQueQuedanDelMes(dia, 90)).as("dia %d", dia).isEqualTo(MesPrograma.semanasQueQuedanDelMes(dia));
        }
        assertThat(MesPrograma.semanasQueQuedanDelMes(331, 365)).isEqualTo(5);
        assertThat(MesPrograma.semanasQueQuedanDelMes(365, 365)).isEqualTo(1);
        assertThat(MesPrograma.semanasQueQuedanDelMes(1, 365)).isEqualTo(5);
    }

    @Test
    @DisplayName("la curva 40 / 75 / 100 se estira: 3 meses la dejan igual y en 12 el primer tercio del programa rinde el 40 %")
    void laCurva() {
        assertThat(CurvaDelPlan.acumuladoAlCierreDe(1, 3)).isEqualByComparingTo("0.40");
        assertThat(CurvaDelPlan.acumuladoAlCierreDe(2, 3)).isEqualByComparingTo("0.75");
        assertThat(CurvaDelPlan.acumuladoAlCierreDe(3, 3)).isEqualByComparingTo("1");
        for (int mes = 1; mes <= 3; mes++) {
            assertThat(CurvaDelPlan.acumuladoAlCierreDe(mes, 3)).isEqualByComparingTo(CurvaDelPlan.acumuladoAlCierreDe(mes));
        }
        assertThat(CurvaDelPlan.acumuladoAlCierreDe(4, 12)).isEqualByComparingTo("0.40");
        assertThat(CurvaDelPlan.acumuladoAlCierreDe(8, 12)).isEqualByComparingTo("0.75");
        assertThat(CurvaDelPlan.acumuladoAlCierreDe(12, 12)).isEqualByComparingTo("1");
        assertThat(CurvaDelPlan.acumuladoAlCierreDe(1, 12)).isEqualByComparingTo("0.10");
        BigDecimal anterior = BigDecimal.ZERO;
        for (int mes = 1; mes <= 12; mes++) {
            BigDecimal actual = CurvaDelPlan.acumuladoAlCierreDe(mes, 12);
            assertThat(actual).as("mes %d", mes).isGreaterThan(anterior);
            anterior = actual;
        }
        assertThat(CurvaDelPlan.acumuladoAlCierreDe(1, 1)).as("un programa de un solo mes: la meta").isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("el objetivo del mes en 12 meses: de 84 a 78 kg el mes 4 pide 81,6 (el hito de los 30 dias de los 90) y el 12 es la meta")
    void elObjetivoDelMesEnUnAno() {
        BigDecimal base = new BigDecimal("84");
        BigDecimal meta = new BigDecimal("78");

        ObjetivoDelMes mes4 = CalculadoraObjetivoMensual.calcular(4, 1, base, base, meta, Magnitud.NIVEL, null, 12);
        ObjetivoDelMes mes12 = CalculadoraObjetivoMensual.calcular(12, 1, base, base, meta, Magnitud.NIVEL, null, 12);
        ObjetivoDelMes mes1de3 = CalculadoraObjetivoMensual.calcular(1, 1, base, base, meta, Magnitud.NIVEL, null, 3);

        assertThat(((ConCifra) mes4).valor()).isEqualByComparingTo("81.6");
        assertThat(((ConCifra) mes12).valor()).isEqualByComparingTo("78");
        assertThat(((ConCifra) mes1de3).valor()).isEqualByComparingTo("81.6");
        assertThat(mes12.numeroMes()).isEqualTo(12);
        assertThat(mes12.diaDeCierre(365)).isEqualTo(365);
        assertThat(mes12.diaDeCierre()).as("sin duracion, la de 90 dias de siempre").isEqualTo(MesPrograma.ultimoDiaDe(12));
    }

    @Test
    @DisplayName("el tope de ritmo reparte la meta en los meses de ESE programa: pedir en un mes el doble de lo planeado en 12 meses ya no se muestra, pero en 3 meses si")
    void elRitmoPlaneadoSigueLosMeses() {
        BigDecimal base = new BigDecimal("0");
        BigDecimal meta = new BigDecimal("12000");

        ObjetivoDelMes unAno = CalculadoraObjetivoMensual.calcular(1, 1, base, base, meta, Magnitud.NIVEL, null, 12);
        ObjetivoDelMes tresMeses = CalculadoraObjetivoMensual.calcular(1, 1, base, base, meta, Magnitud.NIVEL, null, 3);

        assertThat(unAno).isInstanceOf(ConCifra.class);
        assertThat(tresMeses).isInstanceOf(ConCifra.class);
        assertThat(((ConCifra) unAno).valor()).isEqualByComparingTo("1200");
        assertThat(((ConCifra) tresMeses).valor()).isEqualByComparingTo("4800");
    }

    @Test
    @DisplayName("la semana del mes usa los bloques que le quedan a ESE mes, y en 90 dias da lo de siempre")
    void laSemanaDelMes() {
        BigDecimal base = new BigDecimal("84");
        BigDecimal meta = new BigDecimal("78");
        ObjetivoDelMes mes = CalculadoraObjetivoMensual.calcular(1, 1, base, base, meta, Magnitud.NIVEL, null);

        ObjetivoDeLaSemana igual = ObjetivoDeLaSemana.desde(mes, base, 1, Magnitud.NIVEL);
        ObjetivoDeLaSemana conDuracion = ObjetivoDeLaSemana.desde(mes, base, 1, Magnitud.NIVEL, 90);
        ObjetivoDeLaSemana ultimoMes = ObjetivoDeLaSemana.desde(
                CalculadoraObjetivoMensual.calcular(12, 12, base, base, meta, Magnitud.NIVEL, null, 12), base, 331, Magnitud.NIVEL, 365);

        assertThat(conDuracion).isEqualTo(igual);
        assertThat(ultimoMes.semanasQueQuedan()).isEqualTo(5);
    }
}
