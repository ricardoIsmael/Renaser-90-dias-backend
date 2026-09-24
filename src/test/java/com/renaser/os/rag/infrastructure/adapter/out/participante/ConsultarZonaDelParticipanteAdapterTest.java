package com.renaser.os.rag.infrastructure.adapter.out.participante;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsultarZonaDelParticipanteAdapterTest {

    private final ParticipacionProgramaFinder finder = mock(ParticipacionProgramaFinder.class);
    private final ConsultarZonaDelParticipanteAdapter adapter = new ConsultarZonaDelParticipanteAdapter(finder);
    private final UserId actor = UserId.of(UUID.randomUUID());

    @Test
    @DisplayName("la zona es la de participantes_programa.timezone")
    void zonaDelParticipante() {
        when(finder.deParticipante(actor)).thenReturn(Optional.of(new ParticipacionPrograma(actor, true, 12,
                LocalDate.parse("2026-09-12"), ZoneId.of("America/Bogota"), FasePrograma.PHASE_1_REBIRTH, null, null,
                UserRole.TRAINEE, false, true)));

        assertThat(adapter.de(actor)).isEqualTo(ZoneId.of("America/Bogota"));
    }

    @Test
    @DisplayName("si la persona no existe, la zona del padron")
    void sinParticipante() {
        when(finder.deParticipante(actor)).thenReturn(Optional.empty());

        assertThat(adapter.de(actor)).isEqualTo(ZoneId.of("America/Lima"));
    }
}
