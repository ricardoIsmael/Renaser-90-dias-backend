package com.renaser.os.rocks.domain.model.rocamensual;

import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>Que el plan funcione para TODAS las opciones del Mapa, no solo para el peso.</b>
 *
 * <p>Pregunta del dueno, textual: <i>"esta nueva logica que se esta creando, ¿es de acuerdo para
 * todos? recuerda que el Mapa de Poder hay varias opciones y debe de funcionar para todas, asi sea
 * relaciones"</i>. La respuesta honesta a eso no es un "si": es esta clase, que recorre <b>las
 * diecinueve</b> combinaciones que la V41 siembra en {@code opciones_pregunta_onboarding} y fija
 * que cada una haga lo que tiene que hacer.
 *
 * <p>Si alguien agrega una opcion al Mapa y no la traduce en {@link Magnitud}, cae en "todavia no
 * eligio" y la persona se queda sin cifra sin saber por que. {@code MagnitudTest} cubre la
 * traduccion; esta clase cubre el camino entero, de la opcion del Mapa a la cifra de la semana.
 */
class TodasLasOpcionesDelMapaTest {

    private static final BigDecimal BASE = new BigDecimal("84");
    private static final BigDecimal META = new BigDecimal("78");
    private static final int DIA_1 = 1;

    /** El camino completo: opcion del Mapa -> datos del eje -> mes -> semana. */
    private static ObjetivoDelMes mesDeSalud(String tipo, String unidad) {
        return DatosDelEje.deSalud(MetaCuantitativa.desde(BASE, META, unidad), tipo, unidad).calcular(1, 1);
    }

    private static ObjetivoDelMes mesDeNegocio(String tipo, String periodo) {
        MetaCuantitativa meta = MetaCuantitativa.desde(new BigDecimal("5000"), new BigDecimal("15000"), "S/");
        return DatosDelEje.deNegocio(meta, tipo, periodo).calcular(1, 1);
    }

    @Nested
    @DisplayName("Salud · los ocho tipos de la V41")
    class Salud {

        @ParameterizedTest(name = "{0} da una cifra que se puede mostrar")
        @ValueSource(strings = {"peso", "medidas", "fuerza", "resistencia", "sueno", "otro"})
        void losQueSeReparten(String tipo) {
            ObjetivoDelMes mes = mesDeSalud(tipo, "kg");

            assertThat(mes).isInstanceOf(ObjetivoDelMes.ConCifra.class);
            ObjetivoDeLaSemana semana = ObjetivoDeLaSemana.desde(mes, BASE, DIA_1, Magnitud.NIVEL);
            assertThat(semana).isNotNull();
            assertThat(semana.paso().signum()).isPositive();
        }

        @Test
        @DisplayName("energia es una escala: cifra, pero entera")
        void energia() {
            DatosDelEje datos = DatosDelEje.deSalud(MetaCuantitativa.desde(new BigDecimal("4"),
                    new BigDecimal("8"), "/10"), "energia", "/10");
            ObjetivoDelMes mes = datos.calcular(1, 1);

            assertThat(mes).isInstanceOf(ObjetivoDelMes.ConCifra.class);
            assertThat(((ObjetivoDelMes.ConCifra) mes).valor().scale()).isLessThanOrEqualTo(0);
        }

        @Test
        @DisplayName("condicion clinica NO lleva cifra, y dice por que")
        void condicionClinica() {
            assertThat(mesDeSalud("condicion_clinica", "mg/dL"))
                    .isEqualTo(new ObjetivoDelMes.SinCifra(1, ObjetivoDelMes.MotivoSinCifra.ACOMPANAMIENTO_CLINICO));
        }

        @Test
        @DisplayName("solo el peso tiene tope fisiologico; los demas se rigen por el relativo")
        void soloElPesoTieneTope() {
            assertThat(TopeSaludableDelMes.deSalud("peso", BASE)).isEqualByComparingTo("3.36");
            assertThat(TopeSaludableDelMes.deSalud("medidas", BASE)).isNull();
            assertThat(TopeSaludableDelMes.deSalud("fuerza", BASE)).isNull();
        }
    }

    @Nested
    @DisplayName("Negocio · los ocho tipos por los tres periodos")
    class Negocio {

        @ParameterizedTest(name = "{0} medido por mes es un NIVEL al que hay que llegar")
        @ValueSource(strings = {"facturacion", "utilidad", "ventas", "clientes", "ahorro", "deuda",
                "ingreso_personal", "otro"})
        void porPeriodoEsNivel(String tipo) {
            ObjetivoDelMes mes = mesDeNegocio(tipo, "mensual");

            assertThat(mes).isInstanceOf(ObjetivoDelMes.ConCifra.class);
            assertThat(((ObjetivoDelMes.ConCifra) mes).magnitud()).isEqualTo(Magnitud.NIVEL);
        }

        @ParameterizedTest(name = "{0} acumulado al dia 90 es una suma que se junta")
        @ValueSource(strings = {"facturacion", "ventas", "ahorro"})
        void acumuladoEsOtraCosa(String tipo) {
            ObjetivoDelMes.ConCifra mes = (ObjetivoDelMes.ConCifra) mesDeNegocio(tipo, "acumulado_dia_90");

            assertThat(mes.magnitud()).isEqualTo(Magnitud.ACUMULADO);
            // En acumulado la cifra que se muestra es el tramo del mes, no el total corrido.
            assertThat(mes.cifraQueSeMuestra()).isEqualByComparingTo(mes.paso());
        }

        @Test
        @DisplayName("semanal tambien es nivel: es una tasa, no una suma")
        void semanalEsNivel() {
            assertThat(((ObjetivoDelMes.ConCifra) mesDeNegocio("facturacion", "semanal")).magnitud())
                    .isEqualTo(Magnitud.NIVEL);
        }

        @Test
        @DisplayName("sin periodo elegido no se adivina: no hay cifra")
        void sinPeriodoNoHayCifra() {
            assertThat(mesDeNegocio("facturacion", null))
                    .isEqualTo(new ObjetivoDelMes.SinCifra(1, ObjetivoDelMes.MotivoSinCifra.SIN_TIPO));
        }
    }

    @Nested
    @DisplayName("Relaciones · la escala del 1 al 10")
    class Relaciones {

        @Test
        @DisplayName("SI lleva cifra, entera, aunque su Roca Maestra no tenga meta cuantitativa")
        void relacionesTieneCifra() {
            // Los dos numeros salen del Mapa: la maestra de Relaciones viaja sin meta a proposito.
            DatosDelEje datos = DatosDelEje.deRelaciones(4, 8);
            ObjetivoDelMes mes = datos.calcular(1, 1);

            assertThat(mes).isInstanceOf(ObjetivoDelMes.ConCifra.class);
            ObjetivoDelMes.ConCifra cifra = (ObjetivoDelMes.ConCifra) mes;
            assertThat(cifra.valor()).isEqualByComparingTo("6");
            assertThat(cifra.valor().scale()).isLessThanOrEqualTo(0);
            assertThat(datos.unidad()).isEqualTo("/10");
        }

        @Test
        @DisplayName("y su semana tambien, entera")
        void laSemanaDeRelaciones() {
            DatosDelEje datos = DatosDelEje.deRelaciones(4, 8);
            ObjetivoDeLaSemana semana = ObjetivoDeLaSemana.desde(datos.calcular(1, 1),
                    new BigDecimal("4"), DIA_1, Magnitud.ESCALA);

            assertThat(semana).isNotNull();
            assertThat(semana.valor().scale()).isLessThanOrEqualTo(0);
        }

        @Test
        @DisplayName("sin recorrer el Mapa no hay numeros, y se dice que faltan datos")
        void sinMapaNoHayNumeros() {
            assertThat(DatosDelEje.deRelaciones(null, null).calcular(1, 1))
                    .isEqualTo(new ObjetivoDelMes.SinCifra(1, ObjetivoDelMes.MotivoSinCifra.SIN_DATOS));
        }
    }
}
