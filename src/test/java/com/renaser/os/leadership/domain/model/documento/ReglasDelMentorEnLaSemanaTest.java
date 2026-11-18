package com.renaser.os.leadership.domain.model.documento;

import com.renaser.os.leadership.domain.model.documento.DatosDelMentor.Foco;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Las cuentas propias del documento del mentor (spec 056, FR-036): tendencia, días sin actividad y foco. */
class ReglasDelMentorEnLaSemanaTest {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 14);

    private static DiaDelSemaforo medido(int cumplidos, int objetivos) {
        return new DiaDelSemaforo(DIA, EstadoDiaSemaforo.MEDIDO, cumplidos == 0 && objetivos == 0 ? 0 : 100,
                cumplidos == 0 && objetivos == 0 ? ColorSemaforo.ROJO : ColorSemaforo.VERDE, 3, cumplidos, 1, objetivos);
    }

    @Test
    @DisplayName("spec 056 FR-036: la tendencia compara el porcentaje de esta semana con el de la anterior: sube, baja o igual")
    void laTendencia() {
        assertThat(ReglasDelMentorEnLaSemana.tendencia(new BigDecimal("70"), new BigDecimal("60.5"))).isEqualTo("SUBE");
        assertThat(ReglasDelMentorEnLaSemana.tendencia(new BigDecimal("40"), new BigDecimal("60.5"))).isEqualTo("BAJA");
        assertThat(ReglasDelMentorEnLaSemana.tendencia(new BigDecimal("66.7"), new BigDecimal("66.70"))).isEqualTo("IGUAL");
    }

    @Test
    @DisplayName("spec 056 FR-036: si alguna de las dos semanas no se midió no hay comparación: no se inventa un cero ni una baja")
    void sinComparacion() {
        assertThat(ReglasDelMentorEnLaSemana.tendencia(new BigDecimal("70"), null)).isEqualTo("SIN_COMPARACION");
        assertThat(ReglasDelMentorEnLaSemana.tendencia(null, new BigDecimal("70"))).isEqualTo("SIN_COMPARACION");
        assertThat(ReglasDelMentorEnLaSemana.tendencia(null, null)).isEqualTo("SIN_COMPARACION");
    }

    @Test
    @DisplayName("spec 056 FR-036: un día sin actividad es un día medido sin ningún hábito ni objetivo cumplido; sin nada programado, pausado o sin calcular no cuenta")
    void losDiasSinActividad() {
        List<DiaDelSemaforo> dias = List.of(medido(0, 0), medido(0, 0), medido(2, 0), medido(0, 1), medido(0, 0),
                DiaDelSemaforo.sinPorcentaje(DIA, EstadoDiaSemaforo.SIN_DATOS), DiaDelSemaforo.sinPorcentaje(DIA, EstadoDiaSemaforo.PAUSADO));

        assertThat(ReglasDelMentorEnLaSemana.diasSinActividad(dias)).isEqualTo(3);
        assertThat(ReglasDelMentorEnLaSemana.diasSinActividad(List.of())).isZero();
    }

    @Test
    @DisplayName("spec 056 FR-036: el foco de la semana va por lo más urgente: aprendices en rojo, consultas sin responder, alertas y, si no hay nada, mantener el ritmo")
    void elFoco() {
        assertThat(ReglasDelMentorEnLaSemana.foco(2, 5, 3)).isEqualTo(new Foco("ESCRIBIR_A_LOS_ROJOS", 2));
        assertThat(ReglasDelMentorEnLaSemana.foco(0, 5, 3)).isEqualTo(new Foco("RESPONDER_CONSULTAS", 5));
        assertThat(ReglasDelMentorEnLaSemana.foco(0, 0, 3)).isEqualTo(new Foco("REVISAR_ALERTAS", 3));
        assertThat(ReglasDelMentorEnLaSemana.foco(0, 0, 0)).isEqualTo(new Foco("MANTENER_EL_RITMO", 0));
    }
}
