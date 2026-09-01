package com.renaser.os.habits.domain.model.eleccion;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EleccionDiaSemanalTest {

    private static final Instant AHORA = Instant.parse("2026-08-24T09:00:00Z");

    @Test
    void fechaDentroDeLaSemanaElegidaEsValida() {
        UserId participante = UserId.of(UUID.randomUUID());
        HabitoId habito = HabitoId.of(UUID.randomUUID());
        LocalDate lunes = LocalDate.of(2026, 8, 24);

        EleccionDiaSemanal eleccion = EleccionDiaSemanal.elegir(participante, habito, lunes.plusDays(2), lunes,
                AHORA);

        assertThat(eleccion.fechaEjecucion()).isEqualTo(lunes.plusDays(2));
        assertThat(eleccion.semanaInicio()).isEqualTo(lunes);
    }

    @Test
    void fechaFueraDeLaSemanaElegidaRechazada() {
        UserId participante = UserId.of(UUID.randomUUID());
        HabitoId habito = HabitoId.of(UUID.randomUUID());
        LocalDate lunes = LocalDate.of(2026, 8, 24);

        assertThatThrownBy(() -> EleccionDiaSemanal.elegir(participante, habito, lunes.plusDays(7), lunes, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EleccionDiaSemanal.elegir(participante, habito, lunes.minusDays(1), lunes, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
