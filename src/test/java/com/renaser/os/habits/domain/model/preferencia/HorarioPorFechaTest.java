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

    /**
     * D-122: por el camino normal ya no hay nada que rechazar — `PreferenciaHorario.crear` acomoda
     * el cierre al ultimo instante util del dia antes de que este record lo vea.
     */
    @Test
    void unCierreAnteriorAlInicioLlegaYaAcomodado() {
        var pref = PreferenciaHorario.crear(UserId.of(UUID.randomUUID()), HabitoId.of(UUID.randomUUID()),
                LocalTime.of(9, 0), LocalTime.of(8, 0), Instant.EPOCH);

        assertThat(new HorarioPorFecha(hoy.plusDays(1), pref).preferencia().horaLimite())
                .isEqualTo(LocalTime.of(23, 50));
    }

    /**
     * La guarda defensiva sigue viva para lo unico que la puede disparar: una fila REHIDRATADA de
     * la base, que no pasa por el ajuste (una escrita antes de D-122, por ejemplo).
     */
    @Test
    void unaFilaViejaDeLaBaseConLaVentanaVaciaSigueSiendoRechazada() {
        var cruda = PreferenciaHorario.rehydrate(UserId.of(UUID.randomUUID()), HabitoId.of(UUID.randomUUID()),
                LocalTime.of(9, 0), LocalTime.of(8, 0), true, null, Instant.EPOCH, Instant.EPOCH);

        assertThatThrownBy(() -> new HorarioPorFecha(hoy.plusDays(1), cruda))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("horaLimite");
    }
}
