package com.renaser.os.habits.domain.model.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FechaPropiaTest {

    private final HabitoId habito = HabitoId.of(UUID.randomUUID());
    private final LocalDate fecha = LocalDate.of(2026, 11, 7);

    @Test
    void unaFechaEncendidaNecesitaAlgoPropio() {
        assertThatThrownBy(() -> new FechaPropia(habito, fecha, true, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bastaConLaHoraConElAvisoOConLaAlarma() {
        assertThat(new FechaPropia(habito, fecha, true, LocalTime.of(9, 0), null, null, null).tieneHoraPropia()).isTrue();
        assertThat(new FechaPropia(habito, fecha, true, null, null, RecordatorioDelDia.sinAviso(), null).recordatorio())
                .isEqualTo(RecordatorioDelDia.sinAviso());
        assertThat(new FechaPropia(habito, fecha, true, null, null, null, AjusteDeAlarma.de(CombinacionDeAlarma.COMO_HOY)).alarma())
                .isNotNull();
    }

    @Test
    void unaFechaApagadaNoNecesitaNada() {
        assertThat(FechaPropia.apagada(habito, fecha).activo()).isFalse();
    }

    @Test
    void unAjusteDeAlarmaVacioEsLoMismoQueNingunoYNoBasta() {
        assertThatThrownBy(() -> new FechaPropia(habito, fecha, true, null, null, null, AjusteDeAlarma.hereda()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void copiarlaAOtraFechaConservaTodoLoDemas() {
        var original = new FechaPropia(habito, fecha, true, LocalTime.of(9, 0), null, RecordatorioDelDia.con(List.of(30, 0)), null);

        var copia = original.enLaFecha(fecha.plusDays(7));

        assertThat(copia.fecha()).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(copia.horaDisparo()).isEqualTo(LocalTime.of(9, 0));
        assertThat(copia.recordatorio().antelaciones()).containsExactly(30, 0);
    }
}
