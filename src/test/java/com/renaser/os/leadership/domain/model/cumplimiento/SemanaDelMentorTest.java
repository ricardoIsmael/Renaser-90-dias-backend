package com.renaser.os.leadership.domain.model.cumplimiento;

import com.renaser.os.shared.domain.RechazoConCodigoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La semana que el mentor mira (spec 055): de sábado a viernes, también la que está en curso, entre el Día 1 y hoy.
 */
class SemanaDelMentorTest {

    /** Miércoles 18 de noviembre de 2026 (día local del aprendiz). */
    private static final LocalDate HOY = LocalDate.of(2026, 11, 18);
    /** Lunes 5 de octubre: su semana abre el sábado 3 y cierra el viernes 9. */
    private static final LocalDate DIA_UNO = LocalDate.of(2026, 10, 5);

    @Test
    @DisplayName("spec 055 FR-011: sin fecha es la semana en curso, del sábado al viernes que la cierra, todavía abierta")
    void sinFechaEsLaSemanaEnCurso() {
        VentanaDeLaFicha ventana = SemanaDelMentor.ventana(null, HOY, DIA_UNO);

        assertThat(ventana.desde()).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(ventana.hasta()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(ventana.cerrada()).isFalse();
        assertThat(ventana.hoyLocal()).isEqualTo(HOY);
    }

    @Test
    @DisplayName("spec 055 FR-011: un viernes que ya cerró da esa semana, cerrada; un sábado es la semana que recién abre")
    void unaSemanaCerradaYElSabado() {
        assertThat(SemanaDelMentor.ventana(LocalDate.of(2026, 11, 13), HOY, DIA_UNO).cerrada()).isTrue();
        assertThat(SemanaDelMentor.ventana(null, LocalDate.of(2026, 11, 14), DIA_UNO).desde())
                .as("el sábado ya es la semana nueva").isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(SemanaDelMentor.ventana(null, LocalDate.of(2026, 11, 20), DIA_UNO).hasta())
                .as("el viernes todavía es la de hoy").isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    @DisplayName("spec 055 FR-011: una fecha que no es viernes es SEMANA_INVALIDA")
    void noEsViernes() {
        assertThatThrownBy(() -> SemanaDelMentor.ventana(LocalDate.of(2026, 11, 17), HOY, DIA_UNO))
                .isInstanceOfSatisfying(RechazoConCodigoException.class, e -> assertThat(e.codigo()).isEqualTo("SEMANA_INVALIDA"));
    }

    @Test
    @DisplayName("spec 055 FR-015 FR-017: la semana del Día 1 es la primera; una anterior o una futura es FUERA_DEL_PROGRAMA")
    void entreElDiaUnoYHoy() {
        assertThat(SemanaDelMentor.ventana(LocalDate.of(2026, 10, 9), HOY, DIA_UNO).desde()).isEqualTo(LocalDate.of(2026, 10, 3));
        for (LocalDate viernes : new LocalDate[] {LocalDate.of(2026, 10, 2), LocalDate.of(2026, 11, 27)}) {
            assertThatThrownBy(() -> SemanaDelMentor.ventana(viernes, HOY, DIA_UNO))
                    .isInstanceOfSatisfying(RechazoConCodigoException.class,
                            e -> assertThat(e.codigo()).isEqualTo("FUERA_DEL_PROGRAMA"));
        }
    }

    @Test
    @DisplayName("spec 055 FR-017: sin Día 1 elegido solo se ve la semana en curso")
    void sinDiaUnoSoloLaSemanaEnCurso() {
        assertThat(SemanaDelMentor.ventana(null, HOY, null).hasta()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(SemanaDelMentor.ventana(LocalDate.of(2026, 11, 20), HOY, null).desde()).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThatThrownBy(() -> SemanaDelMentor.ventana(LocalDate.of(2026, 11, 13), HOY, null))
                .isInstanceOfSatisfying(RechazoConCodigoException.class,
                        e -> assertThat(e.codigo()).isEqualTo("FUERA_DEL_PROGRAMA"));
    }
}
