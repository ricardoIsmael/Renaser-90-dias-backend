package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.shared.domain.Formacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 036 (D-309) en SER (tarea T020 de la spec 038): el programa empieza al dia siguiente de confirmarlo, asi que el primer encuentro con SER cae casi
 * siempre en el dia 0. La situacion dice cuando empieza, con el plazo de SU formacion, y no habla de dia ni de fase.
 */
class SituacionAntesDelDiaUnoEnElPromptTest {

    /** Lunes 2 de noviembre de 2026. */
    private static final LocalDate HOY = LocalDate.of(2026, 11, 2);
    private static final LocalDate MANANA = HOY.plusDays(1);

    static Stream<Arguments> formaciones() {
        return Stream.of(
                Arguments.of("Escuela de 90 dias", Formacion.ESCUELA, 90, "90 días"),
                Arguments.of("Certificacion de 90 dias", Formacion.CERTIFICACION, 90, "90 días"),
                Arguments.of("Diplomado de 3 meses", Formacion.DIPLOMADO, 90, "3 meses"),
                Arguments.of("Diplomado de 1 año", Formacion.DIPLOMADO, 365, "1 año"));
    }

    private static SituacionDelAprendiz diaCero(Formacion formacion, int dias, LocalDate diaUno) {
        return new SituacionDelAprendiz(0, 1, HOY).conDuracion(dias).conFormacion(formacion).conDiaUno(diaUno);
    }

    // spec 036 FR-001 (el Dia 1 es siempre mañana): SER lo dice con la fecha y el plazo de la formacion
    @ParameterizedTest(name = "{0}")
    @MethodSource("formaciones")
    @DisplayName("spec 036: en el dia 0 la situacion dice que su programa empieza manana, con la fecha y el plazo de su formacion, y no habla de dia ni de fase")
    void empiezaManana(String nombre, Formacion formacion, int dias, String plazo) {
        String situacion = GoogleGenAiRenasiaChatAdapter.formatearSituacion(diaCero(formacion, dias, MANANA));

        assertThat(situacion).as(nombre).contains("Su programa de " + plazo + " empieza manana (martes 3 de noviembre).")
                .contains("No hables de su dia ni de su fase: todavia no tiene.")
                .doesNotContain("su dia 0").doesNotContain("de 4").doesNotContain("en la fase");
    }

    @Test
    @DisplayName("spec 036: el texto exacto del dia 0 de la Escuela")
    void textoExacto() {
        assertThat(GoogleGenAiRenasiaChatAdapter.programaPorEmpezar(diaCero(Formacion.ESCUELA, 90, MANANA)))
                .isEqualTo("Su programa de 90 días empieza manana (martes 3 de noviembre). "
                        + "No hables de su dia ni de su fase: todavia no tiene.");
    }

    @Test
    @DisplayName("spec 036: quien eligio +2 o +3 dias antes del cambio conserva su fecha: «empieza el jueves 5 de noviembre»")
    void conservaLaFechaQueEligioAntes() {
        assertThat(GoogleGenAiRenasiaChatAdapter.formatearSituacion(diaCero(Formacion.ESCUELA, 90, HOY.plusDays(3))))
                .contains("Su programa de 90 días empieza el jueves 5 de noviembre.");
    }

    @Test
    @DisplayName("spec 036: si el reloj del programa todavia esta en 0 el dia de su Dia 1, dice que empieza hoy")
    void empiezaHoy() {
        assertThat(GoogleGenAiRenasiaChatAdapter.programaPorEmpezar(diaCero(Formacion.ESCUELA, 90, HOY)))
                .contains("empieza hoy (lunes 2 de noviembre)");
    }

    @Test
    @DisplayName("spec 036: quien todavia no activo su programa no recibe una fecha inventada: le dice que lo active con «Empezar manana»")
    void sinActivarNoHayFecha() {
        String situacion = GoogleGenAiRenasiaChatAdapter.formatearSituacion(diaCero(Formacion.DIPLOMADO, 90, null));

        assertThat(situacion).contains("Todavia no activo su programa de 3 meses: empieza al dia siguiente de confirmarlo")
                .contains("«Empezar manana»").contains("No hables de su dia ni de su fase")
                .doesNotContain("empieza manana (").doesNotContain("su dia 0");
    }

    @Test
    @DisplayName("spec 036: sin la fecha de hoy igual dice cuando empieza, sin afirmar que es manana")
    void sinFechaDeHoy() {
        SituacionDelAprendiz sinHoy = new SituacionDelAprendiz(0, 1).conDiaUno(MANANA);

        assertThat(GoogleGenAiRenasiaChatAdapter.formatearSituacion(sinHoy)).contains("empieza el martes 3 de noviembre")
                .doesNotContain("manana");
    }

    @Test
    @DisplayName("spec 036: desde el dia 1 la situacion es la de siempre (dia y fase)")
    void desdeElDiaUnoNoCambia() {
        assertThat(GoogleGenAiRenasiaChatAdapter.formatearSituacion(new SituacionDelAprendiz(1, 1, MANANA).conDiaUno(MANANA)))
                .contains("su dia 1 de 90, en la fase 1 de 4").doesNotContain("empieza");
    }
}
