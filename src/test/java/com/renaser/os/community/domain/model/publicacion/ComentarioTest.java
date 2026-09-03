package com.renaser.os.community.domain.model.publicacion;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComentarioTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
    /** El id ya no lo sortea la factoria: entra por parametro, generado por el puerto IdGenerator. */
    private static final ComentarioId ID = ComentarioId.of(UUID.randomUUID());

    private static Comentario nuevo() {
        return Comentario.escribir(ID, PublicacionId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()),
                "que lindo!", CLOCK.now());
    }

    @Test
    void escribirNaceVisible() {
        Comentario c = nuevo();
        assertThat(c.id()).isEqualTo(ID);
        assertThat(c.oculto()).isFalse();
    }

    @Test
    void textoVacioEsInvalido() {
        assertThatThrownBy(() -> Comentario.escribir(ID, PublicacionId.of(UUID.randomUUID()),
                UserId.of(UUID.randomUUID()), " ", CLOCK.now())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void masDe500CaracteresEsInvalido() {
        String largo = "a".repeat(501);
        assertThatThrownBy(() -> Comentario.escribir(ID, PublicacionId.of(UUID.randomUUID()),
                UserId.of(UUID.randomUUID()), largo, CLOCK.now())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ocultarUnoYaOcultoFalla() {
        Comentario c = nuevo();
        c.ocultar(CLOCK.now());
        assertThatThrownBy(() -> c.ocultar(CLOCK.now())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void editarUnoOcultoFalla() {
        Comentario c = nuevo();
        c.ocultar(CLOCK.now());
        assertThatThrownBy(() -> c.editar("nuevo", CLOCK.now())).isInstanceOf(IllegalStateException.class);
    }
}
