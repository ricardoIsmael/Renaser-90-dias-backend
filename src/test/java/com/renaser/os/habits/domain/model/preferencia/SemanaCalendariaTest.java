package com.renaser.os.habits.domain.model.preferencia;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanaCalendariaTest {

    @Test
    void laSemanaDeUnaFechaVaDeLunesADomingo() {
        // sabado 7 de noviembre de 2026
        SemanaCalendaria semana = SemanaCalendaria.de(LocalDate.of(2026, 11, 7));

        assertThat(semana.lunes()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(semana.domingo()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(semana.fechas()).hasSize(7).first().isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    void elDomingoPerteneceALaSemanaQueTermina() {
        assertThat(SemanaCalendaria.de(LocalDate.of(2026, 11, 8)).lunes()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(SemanaCalendaria.de(LocalDate.of(2026, 11, 9)).lunes()).isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    void unaSemanaQueCruzaDosMesesSeSigueContandoCompleta() {
        SemanaCalendaria semana = SemanaCalendaria.de(LocalDate.of(2026, 11, 1));

        assertThat(semana.lunes()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(semana.fecha(6)).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(semana.siguiente().lunes()).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    void desdeExigeUnLunes() {
        assertThatThrownBy(() -> SemanaCalendaria.desde(LocalDate.of(2026, 11, 3)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elMismoDiaEnOtraSemana() {
        SemanaCalendaria modelo = SemanaCalendaria.de(LocalDate.of(2026, 10, 28));
        SemanaCalendaria destino = modelo.siguiente();

        assertThat(destino.elMismoDiaQue(LocalDate.of(2026, 10, 31), modelo)).isEqualTo(LocalDate.of(2026, 11, 7));
    }

    @Test
    void sabeSiLeQuedanDiasPorCambiar() {
        SemanaCalendaria semana = SemanaCalendaria.de(LocalDate.of(2026, 11, 4));

        assertThat(semana.tieneDiasPorCambiar(LocalDate.of(2026, 11, 7))).isTrue();
        assertThat(semana.tieneDiasPorCambiar(LocalDate.of(2026, 11, 8))).isFalse();
    }
}
