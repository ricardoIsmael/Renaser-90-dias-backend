package com.renaser.os.habits.domain.model.renombre;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RenombreHabitoTest {

    private static final Instant AHORA = Instant.parse("2026-08-26T10:00:00Z");

    private static UserId participante() {
        return UserId.of(UUID.randomUUID());
    }

    @Test
    void creaConTituloYMotivoRecortados() {
        RenombreHabito renombre = RenombreHabito.crear(participante(), HabitoId.newId(), "  Jugo de papaya  ",
                "  Gastritis  ", AHORA);

        assertThat(renombre.tituloPersonal()).isEqualTo("Jugo de papaya");
        assertThat(renombre.motivo()).isEqualTo("Gastritis");
    }

    @Test
    void tituloVacioRechazado() {
        assertThatThrownBy(() -> RenombreHabito.crear(participante(), HabitoId.newId(), "   ", "motivo", AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void motivoVacioRechazado() {
        assertThatThrownBy(() -> RenombreHabito.crear(participante(), HabitoId.newId(), "titulo", "  ", AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tituloDemasiadoLargoRechazado() {
        String largo = "x".repeat(61);
        assertThatThrownBy(() -> RenombreHabito.crear(participante(), HabitoId.newId(), largo, "motivo", AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void motivoDemasiadoLargoRechazado() {
        String largo = "x".repeat(201);
        assertThatThrownBy(() -> RenombreHabito.crear(participante(), HabitoId.newId(), "titulo", largo, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarCambiaTituloYMotivo() {
        RenombreHabito renombre = RenombreHabito.crear(participante(), HabitoId.newId(), "Jugo de papaya",
                "Gastritis", AHORA);
        Instant despues = AHORA.plusSeconds(60);

        renombre.actualizar("Jugo de naranja", "Alergia", despues);

        assertThat(renombre.tituloPersonal()).isEqualTo("Jugo de naranja");
        assertThat(renombre.motivo()).isEqualTo("Alergia");
        assertThat(renombre.actualizadoEn()).isEqualTo(despues);
    }
}
