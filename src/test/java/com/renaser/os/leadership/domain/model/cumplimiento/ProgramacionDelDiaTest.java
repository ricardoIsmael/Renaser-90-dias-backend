package com.renaser.os.leadership.domain.model.cumplimiento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec 018, FR-008 a FR-010: cuándo una persona «programó» su día, con los números de la spec. */
class ProgramacionDelDiaTest {

    private static boolean programo(int habitosConHora, int habitosTotal, int accionesConHora, int accionesTotal) {
        return new ProgramacionDelDia(habitosConHora, habitosTotal, accionesConHora, accionesTotal).programo();
    }

    @Test
    @DisplayName("la mitad justa de los hábitos con hora y todas las acciones agendadas es «Sí»")
    void laMitadJustaCuenta() {
        assertThat(programo(4, 8, 3, 3)).isTrue();
        assertThat(programo(9, 17, 2, 2)).isTrue();
    }

    @Test
    @DisplayName("menos de la mitad de los hábitos con hora es «No», aunque las acciones estén agendadas")
    void menosDeLaMitadEsNo() {
        assertThat(programo(8, 17, 2, 2)).isFalse();
        assertThat(programo(0, 5, 3, 3)).isFalse();
    }

    @Test
    @DisplayName("una sola acción de hoy sin hora es «No», aunque todos los hábitos tengan la suya")
    void unaAccionSinHoraEsNo() {
        assertThat(programo(17, 17, 2, 3)).isFalse();
        assertThat(programo(17, 17, 0, 1)).isFalse();
    }

    @Test
    @DisplayName("sin acciones hoy la decisión la toman los hábitos: no se castiga lo que no se planificó")
    void sinAccionesDecidenLosHabitos() {
        assertThat(programo(9, 17, 0, 0)).isTrue();
        assertThat(programo(5, 17, 0, 0)).isFalse();
    }

    @Test
    @DisplayName("un solo hábito: con su hora es «Sí», sin ella es «No» (la mitad de uno es medio hábito)")
    void unSoloHabito() {
        assertThat(programo(1, 1, 0, 0)).isTrue();
        assertThat(programo(0, 1, 0, 0)).isFalse();
    }

    @Test
    @DisplayName("sin hábitos que le toquen hoy no hay nada que programar: vacío, nunca un «No» por falta de datos")
    void sinHabitosNoHayPregunta() {
        assertThat(ProgramacionDelDia.de(0, 0, 0, 0)).isEmpty();
        assertThat(ProgramacionDelDia.de(0, 0, 2, 2)).isEmpty();
        assertThat(ProgramacionDelDia.de(3, 6, 1, 1)).hasValueSatisfying(p -> assertThat(p.programo()).isTrue());
    }

    @Test
    @DisplayName("las cuentas imposibles se rechazan: más con hora que el total, o números negativos")
    void rechazaCuentasImposibles() {
        assertThatThrownBy(() -> new ProgramacionDelDia(4, 3, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProgramacionDelDia(-1, 3, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProgramacionDelDia(1, 3, 2, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProgramacionDelDia(0, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
