package com.renaser.os.rocks.domain.model.rocamensual;

import com.renaser.os.rocks.domain.model.rocamensual.ObjetivoDelMes.ConCifra;
import com.renaser.os.rocks.domain.model.rocamensual.ObjetivoDelMes.MotivoSinCifra;
import com.renaser.os.rocks.domain.model.rocamensual.ObjetivoDelMes.SinCifra;
import com.renaser.os.rocks.domain.model.rocamensual.ObjetivoDelMes.YaAlcanzado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El objetivo del mes, calculado.
 *
 * <p><b>El test que hubiera atrapado el bug</b> es {@code elMes1SinMedicionesDaExactamenteElHito}:
 * contra el codigo viejo —que repartia en tercios— ese caso daba 82 y el hito del Mapa decia 81,6,
 * que es justo la contradiccion que el dueno vio en pantalla el 2026-09-22. Si alguien vuelve a
 * poner una division lineal, ese test se pone rojo solo.
 */
class CalculadoraObjetivoMensualTest {

    private static final BigDecimal OCHENTA_Y_CUATRO = new BigDecimal("84");
    private static final BigDecimal SETENTA_Y_OCHO = new BigDecimal("78");

    /** Lo que calcula el Mapa para sus hitos: {@code base + (meta - base) * avanceEsperado}. */
    private static BigDecimal hitoDelMapa(BigDecimal base, BigDecimal meta, String avanceEsperado) {
        return base.add(meta.subtract(base).multiply(new BigDecimal(avanceEsperado)));
    }

    private static ObjetivoDelMes peso(int mes, int mesActual, BigDecimal hoy) {
        return CalculadoraObjetivoMensual.calcular(mes, mesActual, OCHENTA_Y_CUATRO, hoy, SETENTA_Y_OCHO,
                Magnitud.NIVEL, null);
    }

    @Nested
    @DisplayName("Unificacion con los hitos del Mapa")
    class Unificacion {

        @Test
        @DisplayName("el mes 1, sin mediciones todavia, da exactamente el hito del Dia 30")
        void elMes1SinMedicionesDaExactamenteElHito() {
            ObjetivoDelMes resultado = peso(1, 1, null);

            BigDecimal esperado = hitoDelMapa(OCHENTA_Y_CUATRO, SETENTA_Y_OCHO, "0.40");
            assertThat(esperado).isEqualByComparingTo("81.6");
            assertThat(resultado).isInstanceOf(ConCifra.class);
            assertThat(((ConCifra) resultado).valor()).isEqualByComparingTo(esperado);
        }

        @Test
        @DisplayName("y NO da el tercio que daba la formula vieja")
        void yaNoDaElTercio() {
            BigDecimal tercio = OCHENTA_Y_CUATRO.subtract(
                    OCHENTA_Y_CUATRO.subtract(SETENTA_Y_OCHO).divide(new BigDecimal("3")));

            assertThat(tercio).isEqualByComparingTo("82");
            assertThat(((ConCifra) peso(1, 1, null)).valor()).isNotEqualByComparingTo(tercio);
        }

        @Test
        @DisplayName("los meses 2 y 3, sin mediciones, tambien caen sobre la curva del Mapa")
        void losTresMesesCaenSobreLaCurva() {
            assertThat(((ConCifra) peso(2, 1, null)).valor())
                    .isEqualByComparingTo(hitoDelMapa(OCHENTA_Y_CUATRO, SETENTA_Y_OCHO, "0.75"));
            assertThat(((ConCifra) peso(3, 1, null)).valor())
                    .isEqualByComparingTo(SETENTA_Y_OCHO);
        }
    }

    @Nested
    @DisplayName("Se recalcula contra el valor real")
    class ContraElValorReal {

        @Test
        @DisplayName("si el mes 1 no se movio nada, el mes 2 pide mas que lo planeado")
        void loQueNoSeHizoSeRedistribuye() {
            ConCifra alPlan = (ConCifra) peso(2, 1, null);
            ConCifra atrasado = (ConCifra) peso(2, 2, OCHENTA_Y_CUATRO);

            // Al plan, el mes 2 cierra en 79,5. Arrastrando el mes 1 entero, pide bajar mas.
            assertThat(alPlan.valor()).isEqualByComparingTo("79.5");
            assertThat(atrasado.paso()).isGreaterThan(alPlan.paso());
        }

        @Test
        @DisplayName("el mes 3 siempre pide todo lo que falta: al Dia 90 se llega o no se llega")
        void elMes3PideTodo() {
            assertThat(((ConCifra) peso(3, 3, new BigDecimal("83"))).valor())
                    .isEqualByComparingTo(SETENTA_Y_OCHO);
            assertThat(((ConCifra) peso(3, 3, new BigDecimal("80"))).valor())
                    .isEqualByComparingTo(SETENTA_Y_OCHO);
        }

        @Test
        @DisplayName("un mes que ya cerro no se reescribe: sigue diciendo lo que se prometio")
        void losMesesCerradosNoSeReescriben() {
            ConCifra mes1DesdeElMes2 = (ConCifra) peso(1, 2, new BigDecimal("83"));

            assertThat(mes1DesdeElMes2.valor())
                    .isEqualByComparingTo(hitoDelMapa(OCHENTA_Y_CUATRO, SETENTA_Y_OCHO, "0.40"));
        }
    }

    @Nested
    @DisplayName("Topes de cordura")
    class Topes {

        @Test
        @DisplayName("bajar mas del 4 % del peso en un mes no lleva cifra")
        void topeAbsolutoDePeso() {
            BigDecimal tope = TopeSaludableDelMes.deSalud("peso", new BigDecimal("84"));
            ObjetivoDelMes resultado = CalculadoraObjetivoMensual.calcular(3, 3, OCHENTA_Y_CUATRO,
                    OCHENTA_Y_CUATRO, new BigDecimal("64"), Magnitud.NIVEL, tope);

            assertThat(resultado).isEqualTo(new SinCifra(3, MotivoSinCifra.RITMO_NO_SALUDABLE));
        }

        @Test
        @DisplayName("donde hay tope absoluto, el relativo no opina")
        void elTopeAbsolutoDesplazaAlRelativo() {
            // 2,5 kg en el ultimo mes es mas que el ritmo planeado (2 kg/mes) pero perfectamente sano.
            BigDecimal tope = TopeSaludableDelMes.deSalud("peso", new BigDecimal("80.5"));
            ObjetivoDelMes resultado = CalculadoraObjetivoMensual.calcular(3, 3, OCHENTA_Y_CUATRO,
                    new BigDecimal("80.5"), SETENTA_Y_OCHO, Magnitud.NIVEL, tope);

            assertThat(resultado).isInstanceOf(ConCifra.class);
        }

        @Test
        @DisplayName("sin tope absoluto manda el relativo: mas de 3 veces el ritmo planeado")
        void topeRelativo() {
            // Plan: de 5 000 a 15 000 (ritmo 3 333/mes). Retrocedio a 1 000 y le queda un mes.
            ObjetivoDelMes resultado = CalculadoraObjetivoMensual.calcular(3, 3, new BigDecimal("5000"),
                    new BigDecimal("1000"), new BigDecimal("15000"), Magnitud.NIVEL, null);

            assertThat(resultado).isEqualTo(new SinCifra(3, MotivoSinCifra.FUERA_DE_ALCANCE));
        }

        @Test
        @DisplayName("no arrancar un mes entero todavia se muestra: es 1,5 veces el ritmo, no 3")
        void arrastrarUnMesNoApagaLaCifra() {
            assertThat(peso(2, 2, OCHENTA_Y_CUATRO)).isInstanceOf(ConCifra.class);
        }
    }

    @Nested
    @DisplayName("Cuando no hay numero, se dice por que")
    class SinNumero {

        @Test
        @DisplayName("sin elegir que se mide no hay nada que repartir")
        void sinTipo() {
            assertThat(CalculadoraObjetivoMensual.calcular(1, 1, OCHENTA_Y_CUATRO, null, SETENTA_Y_OCHO, null, null))
                    .isEqualTo(new SinCifra(1, MotivoSinCifra.SIN_TIPO));
        }

        @Test
        @DisplayName("una condicion clinica no se reparte en cuotas")
        void clinico() {
            assertThat(CalculadoraObjetivoMensual.calcular(1, 1, OCHENTA_Y_CUATRO, null, SETENTA_Y_OCHO,
                    Magnitud.CLINICO, null))
                    .isEqualTo(new SinCifra(1, MotivoSinCifra.ACOMPANAMIENTO_CLINICO));
        }

        @Test
        @DisplayName("falta la linea base o la meta")
        void sinDatos() {
            assertThat(CalculadoraObjetivoMensual.calcular(1, 1, null, null, SETENTA_Y_OCHO, Magnitud.NIVEL, null))
                    .isEqualTo(new SinCifra(1, MotivoSinCifra.SIN_DATOS));
            assertThat(CalculadoraObjetivoMensual.calcular(1, 1, OCHENTA_Y_CUATRO, null, null, Magnitud.NIVEL, null))
                    .isEqualTo(new SinCifra(1, MotivoSinCifra.SIN_DATOS));
        }

        @Test
        @DisplayName("partir y llegar al mismo numero no es un recorrido")
        void sinRecorrido() {
            assertThat(CalculadoraObjetivoMensual.calcular(1, 1, SETENTA_Y_OCHO, null, SETENTA_Y_OCHO,
                    Magnitud.NIVEL, null))
                    .isEqualTo(new SinCifra(1, MotivoSinCifra.SIN_RECORRIDO));
        }

        @Test
        @DisplayName("llegar a la meta antes de tiempo no pide otro tramo")
        void yaAlcanzado() {
            assertThat(peso(2, 2, new BigDecimal("77")))
                    .isEqualTo(new YaAlcanzado(2, SETENTA_Y_OCHO));
        }
    }

    @Nested
    @DisplayName("Escalas del 1 al 10")
    class Escalas {

        @Test
        @DisplayName("se reparten, pero enteras: nadie actua sobre tres decimas de punto")
        void laEscalaVaEntera() {
            // De 4 a 8: el 40 % del camino es 1,6 puntos, que se muestra como 6 (4 + 1,6 = 5,6).
            ConCifra resultado = (ConCifra) CalculadoraObjetivoMensual.calcular(1, 1, new BigDecimal("4"),
                    null, new BigDecimal("8"), Magnitud.ESCALA, null);

            assertThat(resultado.valor()).isEqualByComparingTo("6");
            assertThat(resultado.valor().scale()).isLessThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Metas acumuladas")
    class Acumuladas {

        @Test
        @DisplayName("la cifra que se muestra es el tramo del mes, no el total corrido")
        void enAcumuladoSeMuestraElPaso() {
            ConCifra resultado = (ConCifra) CalculadoraObjetivoMensual.calcular(1, 1, BigDecimal.ZERO, null,
                    new BigDecimal("30000"), Magnitud.ACUMULADO, null);

            assertThat(resultado.valor()).isEqualByComparingTo("12000");
            assertThat(resultado.cifraQueSeMuestra()).isEqualByComparingTo(resultado.paso());
            assertThat(resultado.cifraQueSeMuestra()).isEqualByComparingTo("12000");
        }
    }
}
