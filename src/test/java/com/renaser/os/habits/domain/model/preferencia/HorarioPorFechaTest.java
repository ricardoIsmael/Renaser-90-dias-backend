package com.renaser.os.habits.domain.model.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class HorarioPorFechaTest {
    private final LocalDate hoy = LocalDate.of(2026, 9, 7);

    @Test
    void conservaLaFechaExactaYAdmiteHorarioSinCierre() {
        var pref = PreferenciaHorario.crear(UserId.of(UUID.randomUUID()), HabitoId.of(UUID.randomUUID()),
                LocalTime.of(9, 0), null, Instant.EPOCH);
        var horario = new HorarioPorFecha(hoy.plusDays(2), pref);
        HorarioPorFecha.requirePlanificable(horario.fecha(), hoy);
        assertThat(horario.fecha()).isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(horario.preferencia().horaLimite()).isNull();
    }

    @Test
    void rechazaHoyYPasado() {
        for (var fecha : java.util.List.of(hoy, hoy.minusDays(1))) {
            assertThatThrownBy(() -> HorarioPorFecha.requirePlanificable(fecha, hoy))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rechazaCierreAnteriorALaHoraDeInicio() {
        var pref = PreferenciaHorario.crear(UserId.of(UUID.randomUUID()), HabitoId.of(UUID.randomUUID()),
                LocalTime.of(9, 0), LocalTime.of(8, 0), Instant.EPOCH);
        assertThatThrownBy(() -> new HorarioPorFecha(hoy.plusDays(1), pref))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
