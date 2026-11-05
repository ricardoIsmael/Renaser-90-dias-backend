package com.renaser.os.calendar.domain.model.meet;

import com.renaser.os.calendar.domain.model.evento.EventoId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdDeEventoDeGoogleTest {

    private static final EventoId EVENTO = EventoId.of(UUID.fromString("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"));

    // spec 040 FR-002, FR-007
    @Test
    @DisplayName("FR-002: el id de Google de un evento que no se repite es el uuid sin guiones, siempre el mismo (así crear dos veces da 409 y no dos eventos)")
    void unEventoSoloEsElUuidSinGuiones() {
        String id = IdDeEventoDeGoogle.de(EVENTO, LocalDate.of(2026, 11, 5), false);

        assertThat(id).isEqualTo("0a1b2c3d4e5f4a6b8c7d9e0f1a2b3c4d");
        assertThat(IdDeEventoDeGoogle.de(EVENTO, LocalDate.of(2027, 3, 9), false)).as("la fecha no cuenta si no se repite").isEqualTo(id);
    }

    // spec 040 FR-002, FR-017
    @Test
    @DisplayName("FR-017: cada fecha de un evento que se repite tiene su propio id, con la fecha pegada: AAAAMMDD")
    void cadaFechaDeUnaSerieTieneSuId() {
        assertThat(IdDeEventoDeGoogle.de(EVENTO, LocalDate.of(2026, 11, 10), true)).isEqualTo("0a1b2c3d4e5f4a6b8c7d9e0f1a2b3c4d20261110");
        assertThat(IdDeEventoDeGoogle.de(EVENTO, LocalDate.of(2026, 11, 17), true))
                .isNotEqualTo(IdDeEventoDeGoogle.de(EVENTO, LocalDate.of(2026, 11, 10), true));
        assertThat(IdDeEventoDeGoogle.de(EVENTO, LocalDate.of(2026, 1, 5), true)).endsWith("20260105");
    }

    // spec 040 FR-002
    @Test
    @DisplayName("FR-002: el id es base32hex válido para Google (solo 0-9 y a-v, entre 5 y 1024 caracteres)")
    void esUnIdValidoParaGoogle() {
        for (int i = 0; i < 200; i++) {
            EventoId evento = EventoId.of(UUID.nameUUIDFromBytes(("evento-" + i).getBytes()));
            assertThat(IdDeEventoDeGoogle.de(evento, LocalDate.of(2026, 12, 31), true)).matches("[0-9a-v]{5,1024}");
            assertThat(IdDeEventoDeGoogle.de(evento, LocalDate.of(2026, 12, 31), false)).matches("[0-9a-v]{5,1024}");
        }
    }
}
