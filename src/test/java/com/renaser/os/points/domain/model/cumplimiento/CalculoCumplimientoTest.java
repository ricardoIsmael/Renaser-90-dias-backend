package com.renaser.os.points.domain.model.cumplimiento;

import com.renaser.os.points.api.EstadoEvaluacion;
import com.renaser.os.points.api.EvaluacionCumplimiento;
import com.renaser.os.points.api.ObligacionEvidencia;
import com.renaser.os.points.api.VentanaEvaluacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La fórmula de plan.md §8, fijada por sus propios ejemplos. El más importante es el que
 * dice qué <b>no</b> es: 75 %, no 71,43 %. La diferencia entre promediar porcentajes y
 * dividir totales es que la segunda pondera a cada alumno por cuántas obligaciones tuvo, y
 * entonces un mentor con un alumno muy exigente y otro tranquilo cobra una nota que no
 * describe a ninguno de los dos.
 */
class CalculoCumplimientoTest {

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID LUIS = UUID.randomUUID();

    private static final Instant INICIO_MES = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant FIN_MES = Instant.parse("2026-10-01T00:00:00Z");
    private static final VentanaEvaluacion MES = new VentanaEvaluacion(INICIO_MES, FIN_MES);

    private static Instant dia(int d) {
        return Instant.parse("2026-09-" + String.format("%02d", d) + "T12:00:00Z");
    }

    /** Una obligación vencida el día indicado, entregada o no. */
    private static ObligacionEvidencia obligacion(UUID aprendiz, int diaVence, Integer diaEntrega) {
        return new ObligacionEvidencia(UUID.randomUUID(), aprendiz, dia(diaVence),
                diaEntrega == null ? null : dia(diaEntrega), false);
    }

    @Test
    @DisplayName("Ana 2/4 y Luis 3/3 dan 75 %, no 71,43 %")
    void promedioDePorcentajesNoDeTotales() {
        List<ObligacionEvidencia> obligaciones = List.of(
                obligacion(ANA, 3, 3), obligacion(ANA, 5, 5), obligacion(ANA, 7, null), obligacion(ANA, 9, null),
                obligacion(LUIS, 3, 3), obligacion(LUIS, 5, 5), obligacion(LUIS, 7, 7));

        EvaluacionCumplimiento evaluacion = CalculoCumplimiento.evaluar(obligaciones,
                Map.of(ANA, List.of(MES), LUIS, List.of(MES)));

        assertThat(evaluacion.porcentaje()).isEqualByComparingTo("75");
        assertThat(evaluacion.porcentaje()).isNotEqualByComparingTo("71.43");
        assertThat(evaluacion.entregadas()).isEqualTo(5);
        assertThat(evaluacion.esperadas()).isEqualTo(7);
        assertThat(evaluacion.aprendicesEvaluados()).isEqualTo(2);
        assertThat(evaluacion.estado()).isEqualTo(EstadoEvaluacion.CALCULADA);
    }

    @Test
    @DisplayName("varios archivos para la misma obligacion cuentan una sola entrega")
    void reenviosNoSumanOtraEntrega() {
        UUID mismaObligacion = UUID.randomUUID();
        List<ObligacionEvidencia> obligaciones = List.of(
                new ObligacionEvidencia(mismaObligacion, ANA, dia(3), dia(3), false),
                new ObligacionEvidencia(mismaObligacion, ANA, dia(3), dia(4), false),
                new ObligacionEvidencia(mismaObligacion, ANA, dia(3), dia(5), false),
                obligacion(ANA, 6, null));

        EvaluacionCumplimiento evaluacion = CalculoCumplimiento.evaluar(obligaciones, Map.of(ANA, List.of(MES)));

        assertThat(evaluacion.esperadas()).isEqualTo(2);
        assertThat(evaluacion.entregadas()).isEqualTo(1);
        assertThat(evaluacion.porcentaje()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("sin obligaciones vencidas el resultado es SIN_MUESTRA, no 0 %")
    void sinMuestraNoEsCero() {
        EvaluacionCumplimiento evaluacion = CalculoCumplimiento.evaluar(List.of(), Map.of(ANA, List.of(MES)));

        assertThat(evaluacion.estado()).isEqualTo(EstadoEvaluacion.SIN_MUESTRA);
        assertThat(evaluacion.porcentaje()).isNull();
        assertThat(evaluacion.esperadas()).isZero();
    }

    @Test
    @DisplayName("un alumno sin obligaciones no arrastra el promedio a la baja: queda excluido")
    void alumnoSinMuestraSeExcluyeDelPromedio() {
        List<ObligacionEvidencia> obligaciones = List.of(
                obligacion(ANA, 3, 3), obligacion(ANA, 5, 5));

        EvaluacionCumplimiento evaluacion = CalculoCumplimiento.evaluar(obligaciones,
                Map.of(ANA, List.of(MES), LUIS, List.of(MES)));

        assertThat(evaluacion.porcentaje()).isEqualByComparingTo("100");
        assertThat(evaluacion.aprendicesEvaluados()).isEqualTo(1);
        assertThat(evaluacion.aprendicesExcluidos()).isEqualTo(1);
    }

    @Test
    @DisplayName("una obligacion que vence fuera de la ventana del mentor no es suya")
    void obligacionFueraDeLaVentanaNoCuenta() {
        VentanaEvaluacion primeraQuincena = new VentanaEvaluacion(INICIO_MES, dia(15));

        List<ObligacionEvidencia> obligaciones = List.of(
                obligacion(ANA, 3, 3), obligacion(ANA, 20, null), obligacion(ANA, 25, null));

        EvaluacionCumplimiento evaluacion = CalculoCumplimiento.evaluar(obligaciones,
                Map.of(ANA, List.of(primeraQuincena)));

        assertThat(evaluacion.esperadas()).isEqualTo(1);
        assertThat(evaluacion.entregadas()).isEqualTo(1);
        assertThat(evaluacion.porcentaje()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("la deuda del tutor anterior no viaja al nuevo (P-05)")
    void deudaAnteriorNoSeTrasladar() {
        VentanaEvaluacion segundaQuincena = new VentanaEvaluacion(dia(15), FIN_MES);

        List<ObligacionEvidencia> obligaciones = List.of(
                obligacion(ANA, 3, null), obligacion(ANA, 5, null),
                obligacion(ANA, 20, 20), obligacion(ANA, 25, 25));

        EvaluacionCumplimiento evaluacion = CalculoCumplimiento.evaluar(obligaciones,
                Map.of(ANA, List.of(segundaQuincena)));

        assertThat(evaluacion.esperadas()).isEqualTo(2);
        assertThat(evaluacion.porcentaje()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("entrega posterior al corte cuenta como tardia fuera de ventana, no como entregada")
    void entregaTardiaFueraDeVentana() {
        VentanaEvaluacion primeraQuincena = new VentanaEvaluacion(INICIO_MES, dia(15));

        List<ObligacionEvidencia> obligaciones = List.of(
                obligacion(ANA, 3, 3),
                obligacion(ANA, 10, 20));

        EvaluacionCumplimiento evaluacion = CalculoCumplimiento.evaluar(obligaciones,
                Map.of(ANA, List.of(primeraQuincena)));

        assertThat(evaluacion.esperadas()).isEqualTo(2);
        assertThat(evaluacion.entregadas()).isEqualTo(1);
        assertThat(evaluacion.tardiasFueraDeVentana()).isEqualTo(1);
        assertThat(evaluacion.porcentaje()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("dos tramos del mismo alumno en el mes se unen antes de sacar su porcentaje")
    void tramosDelMismoAlumnoSeUnen() {
        VentanaEvaluacion tramoUno = new VentanaEvaluacion(INICIO_MES, dia(10));
        VentanaEvaluacion tramoDos = new VentanaEvaluacion(dia(20), FIN_MES);

        List<ObligacionEvidencia> obligaciones = List.of(
                obligacion(ANA, 3, 3), obligacion(ANA, 5, null),
                obligacion(ANA, 15, null),
                obligacion(ANA, 22, 22), obligacion(ANA, 25, 25));

        EvaluacionCumplimiento evaluacion = CalculoCumplimiento.evaluar(obligaciones,
                Map.of(ANA, List.of(tramoUno, tramoDos)));

        assertThat(evaluacion.esperadas()).isEqualTo(4);
        assertThat(evaluacion.entregadas()).isEqualTo(3);
        assertThat(evaluacion.porcentaje()).isEqualByComparingTo("75");
        assertThat(evaluacion.aprendicesEvaluados()).isEqualTo(1);
    }

    @Test
    @DisplayName("la verificacion se cuenta aparte y no cambia el porcentaje (D-03)")
    void verificacionEsAparte() {
        List<ObligacionEvidencia> obligaciones = List.of(
                new ObligacionEvidencia(UUID.randomUUID(), ANA, dia(3), dia(3), true),
                new ObligacionEvidencia(UUID.randomUUID(), ANA, dia(5), dia(5), false));

        EvaluacionCumplimiento evaluacion = CalculoCumplimiento.evaluar(obligaciones, Map.of(ANA, List.of(MES)));

        assertThat(evaluacion.porcentaje()).isEqualByComparingTo("100");
        assertThat(evaluacion.verificadas()).isEqualTo(1);
    }

    @Test
    @DisplayName("sin ningun tramo asignado no hay historia que evaluar")
    void sinTramosEsSinHistorial() {
        EvaluacionCumplimiento evaluacion = CalculoCumplimiento.evaluar(List.of(), Map.of());

        assertThat(evaluacion.estado()).isEqualTo(EstadoEvaluacion.SIN_HISTORIAL);
        assertThat(evaluacion.porcentaje()).isNull();
    }

    @Test
    @DisplayName("el porcentaje no se redondea al calcular: 1/3 conserva decimales")
    void noSeRedondeaAlCalcular() {
        List<ObligacionEvidencia> obligaciones = List.of(
                obligacion(ANA, 3, 3), obligacion(ANA, 5, null), obligacion(ANA, 7, null));

        EvaluacionCumplimiento evaluacion = CalculoCumplimiento.evaluar(obligaciones, Map.of(ANA, List.of(MES)));

        assertThat(evaluacion.porcentaje().doubleValue()).isCloseTo(33.3333,
                org.assertj.core.data.Offset.offset(0.0001));
    }
}
