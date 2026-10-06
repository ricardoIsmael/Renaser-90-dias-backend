package com.renaser.os.calendar.domain.model.asistencia;

import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MarcaDeAsistenciaTest {

    private static final Instant SLOT = Instant.parse("2026-10-06T01:00:00Z");
    private static final Instant A_LAS_2002 = Instant.parse("2026-10-06T01:02:00Z");
    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId lider = UserId.of(UUID.randomUUID());
    private final MarcaDeAsistencia aTiempo = new MarcaDeAsistencia(EventoId.of(UUID.randomUUID()), SLOT,
            UserId.of(UUID.randomUUID()), EstadoAsistencia.A_TIEMPO, admin, A_LAS_2002);

    @Test
    @DisplayName("pedir el mismo estado no mueve la hora ni el autor (un reintento no cambia lo que se ve)")
    void mismoEstadoEsLaMismaMarca() {
        assertThat(aTiempo.remarcar(EstadoAsistencia.A_TIEMPO, lider, A_LAS_2002.plusSeconds(600))).isSameAs(aTiempo);
    }

    @Test
    @DisplayName("pasar a tarde guarda quién y cuándo lo cambió")
    void cambiarATardeGuardaElAutorNuevo() {
        Instant despues = A_LAS_2002.plusSeconds(60);

        MarcaDeAsistencia tarde = aTiempo.remarcar(EstadoAsistencia.TARDE, lider, despues);

        assertThat(tarde.estado()).isEqualTo(EstadoAsistencia.TARDE);
        assertThat(tarde.marcadoPor()).isEqualTo(lider);
        assertThat(tarde.marcadoEn()).isEqualTo(despues);
        assertThat(tarde.usuarioId()).isEqualTo(aTiempo.usuarioId());
    }
}
