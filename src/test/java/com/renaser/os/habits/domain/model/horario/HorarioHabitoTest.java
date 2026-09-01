package com.renaser.os.habits.domain.model.horario;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HorarioHabitoTest {

    private static final Instant AHORA = Instant.parse("2026-08-26T10:00:00Z");

    @Test
    void actualizarRangoCambiaDiaInicioDiaFinYTipoDia() {
        HorarioHabito horario = HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), 1, 10, TipoDia.DISCIPLINA, LocalTime.of(6, 0), LocalTime.of(9, 0),
                AHORA);
        Instant despues = AHORA.plusSeconds(60);

        horario.actualizarRango(5, 20, TipoDia.TODOS, despues);

        assertThat(horario.diaInicio()).isEqualTo(5);
        assertThat(horario.diaFin()).isEqualTo(20);
        assertThat(horario.tipoDia()).isEqualTo(TipoDia.TODOS);
        assertThat(horario.actualizadoEn()).isEqualTo(despues);
    }

    @Test
    void actualizarRangoAceptaDiaFinNuloParaDejarloAbierto() {
        HorarioHabito horario = HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), 1, 10, TipoDia.DISCIPLINA, null, null, AHORA);

        horario.actualizarRango(1, null, TipoDia.DISCIPLINA, AHORA.plusSeconds(1));

        assertThat(horario.diaFin()).isNull();
    }

    @Test
    void actualizarRangoConDiaInicioFueraDeRangoFalla() {
        HorarioHabito horario = HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), 1, null, TipoDia.DISCIPLINA, null, null, AHORA);

        assertThatThrownBy(() -> horario.actualizarRango(0, null, TipoDia.DISCIPLINA, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> horario.actualizarRango(91, null, TipoDia.DISCIPLINA, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarRangoConDiaFinAnteriorADiaInicioFalla() {
        HorarioHabito horario = HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), 10, null, TipoDia.DISCIPLINA, null, null, AHORA);

        assertThatThrownBy(() -> horario.actualizarRango(10, 5, TipoDia.DISCIPLINA, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarRangoConTipoDiaNuloFalla() {
        HorarioHabito horario = HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), 1, null, TipoDia.DISCIPLINA, null, null, AHORA);

        assertThatThrownBy(() -> horario.actualizarRango(1, null, null, AHORA))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void actualizarHorasSiguenPudiendoLimpiarseANull() {
        HorarioHabito horario = HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), 1, null, TipoDia.DISCIPLINA, LocalTime.of(6, 0), LocalTime.of(9, 0),
                AHORA);

        horario.actualizarHoras(null, null, AHORA.plusSeconds(1));

        assertThat(horario.horaDisparo()).isNull();
        assertThat(horario.horaLimite()).isNull();
    }
}
