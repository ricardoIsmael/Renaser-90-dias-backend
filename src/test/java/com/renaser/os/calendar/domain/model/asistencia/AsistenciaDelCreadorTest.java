package com.renaser.os.calendar.domain.model.asistencia;

import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AsistenciaDelCreadorTest {

    // spec 044 FR-027
    @Test
    @DisplayName("FR-027: el mentor que creó el evento ve quién respondió y pasa lista; otro mentor no")
    void elMentorCreador() {
        UserId creador = UserId.of(UUID.randomUUID());

        assertThat(QuienLlevaLaLista.puede(RolUsuario.MENTOR, creador, creador)).isTrue();
        assertThat(QuienLlevaLaLista.puede(RolUsuario.MENTOR, UserId.of(UUID.randomUUID()), creador)).isFalse();
    }
}
