package com.renaser.os.community.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoBasico;
import com.renaser.os.community.api.AcompanamientoFinder.TramoDeAcompanamiento;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** La única regla de la zona de quien acompaña: programa si participa, cohorte de su primer grupo, Lima (la usan el aviso de rojo y el resumen del líder). */
class ZonaDelAcompananteServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-21T00:05:00Z");
    private final UserId mentor = UserId.of(UUID.randomUUID());
    private final UUID grupo = UUID.randomUUID();
    private final AcompanamientoFinder acompanamiento = mock(AcompanamientoFinder.class);
    private final ParticipacionProgramaFinder participaciones = mock(ParticipacionProgramaFinder.class);
    private final ZonaDelAcompananteService servicio = new ZonaDelAcompananteService(acompanamiento, participaciones);

    private static ParticipacionPrograma participacion(UserId id, boolean inscrito, String zona) {
        return new ParticipacionPrograma(id, inscrito, 3, LocalDate.of(2026, 9, 1), ZoneId.of(zona),
                FasePrograma.PHASE_1_REBIRTH, null, null, UserRole.MENTOR, false, true);
    }

    private void conGrupo(String zona) {
        when(acompanamiento.tramosDeAcompanante(any(), any(), any())).thenReturn(List.of(new TramoDeAcompanamiento(grupo, "Fénix", AHORA, null)));
        when(acompanamiento.grupo(grupo)).thenReturn(Optional.of(new GrupoBasico(grupo, "Fénix", UUID.randomUUID(), zona)));
    }

    @Test
    @DisplayName("la zona de su programa manda si participa (inscrito), aunque su grupo esté en otra")
    void delPrograma() {
        when(participaciones.deParticipante(mentor)).thenReturn(Optional.of(participacion(mentor, true, "Europe/Madrid")));
        conGrupo("Asia/Tokyo");

        assertThat(servicio.zonaDe(mentor, AHORA)).isEqualTo(ZoneId.of("Europe/Madrid"));
    }

    @Test
    @DisplayName("si no participa (o no está inscrito) usa la zona de la cohorte de su primer grupo")
    void delPrimerGrupo() {
        when(participaciones.deParticipante(mentor)).thenReturn(Optional.of(participacion(mentor, false, "Asia/Tokyo")));
        conGrupo("Asia/Kolkata");

        assertThat(servicio.zonaDe(mentor, AHORA)).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    @DisplayName("sin programa y sin grupo, o con una zona inválida, es America/Lima")
    void limaPorDefecto() {
        when(participaciones.deParticipante(mentor)).thenReturn(Optional.empty());
        when(acompanamiento.tramosDeAcompanante(any(), any(), any())).thenReturn(List.of());
        assertThat(servicio.zonaDe(mentor, AHORA)).isEqualTo(ZoneId.of("America/Lima"));

        conGrupo("esto-no-es-una-zona");
        assertThat(servicio.zonaDe(mentor, AHORA)).isEqualTo(ZoneId.of("America/Lima"));
    }
}
