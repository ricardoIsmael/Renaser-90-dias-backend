package com.renaser.os.points.domain.model.semaforo;

import com.renaser.os.points.api.SemanaDelSemaforo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La semana del semáforo va de sábado a viernes y se cierra el sábado 00:00 local. */
class SemanaDelSemaforoTest {

    private static final LocalDate VIERNES_25 = LocalDate.of(2026, 9, 25);
    private static final LocalDate VIERNES_18 = LocalDate.of(2026, 9, 18);

    @ParameterizedTest(name = "el {0} la ultima semana cerrada termina el {1}")
    @CsvSource({
            "2026-09-26,2026-09-25",   // sábado: acaba de cerrar la de ayer
            "2026-09-27,2026-09-25",   // domingo
            "2026-09-28,2026-09-25",   // lunes
            "2026-09-25,2026-09-18",   // viernes: la de hoy todavía corre
            "2026-09-24,2026-09-18"})  // jueves
    void laUltimaSemanaCerradaTerminaElViernesAnterior(LocalDate hoy, LocalDate esperado) {
        assertThat(SemanaDelSemaforo.ultimaCerradaAl(hoy)).isEqualTo(esperado);
    }

    @Test
    void empiezaElSabadoAnteriorAlCierre() {
        assertThat(SemanaDelSemaforo.desde(VIERNES_25)).isEqualTo(LocalDate.of(2026, 9, 19));
    }

    @Test
    void elCierreDeUnDiaEsElViernesDeSuSemana() {
        assertThat(SemanaDelSemaforo.cierreDe(LocalDate.of(2026, 9, 19))).isEqualTo(VIERNES_25);
        assertThat(SemanaDelSemaforo.cierreDe(VIERNES_18)).isEqualTo(VIERNES_18);
    }

    @Test
    void soloUnViernesCierraUnaSemana() {
        assertThat(SemanaDelSemaforo.esCierreValido(VIERNES_25)).isTrue();
        assertThat(SemanaDelSemaforo.esCierreValido(VIERNES_25.plusDays(1))).isFalse();
        assertThatThrownBy(() -> SemanaDelSemaforo.desde(VIERNES_25.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
