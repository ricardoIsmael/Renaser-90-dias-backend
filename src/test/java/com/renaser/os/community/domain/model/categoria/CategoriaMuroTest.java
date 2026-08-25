package com.renaser.os.community.domain.model.categoria;

import com.renaser.os.shared.domain.FixedClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** wall-categories/schema.ts:13-29 (forma de la clave) y service.ts:155-168 (sistema no
 * se puede retirar). */
class CategoriaMuroTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Test
    void crearNaceActivaYNoDeSistema() {
        CategoriaMuro c = CategoriaMuro.crear("REVELACIONES", "Revelaciones", "✨", 1, CLOCK.now());
        assertThat(c.activa()).isTrue();
        assertThat(c.esSistema()).isFalse();
    }

    @Test
    void claveEnMinusculasEsInvalida() {
        assertThatThrownBy(() -> CategoriaMuro.crear("revelaciones", "Revelaciones", "✨", 1, CLOCK.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void claveConAcentosEsInvalida() {
        assertThatThrownBy(() -> CategoriaMuro.crear("AGRADECIMIENTOÑ", "X", "🙏", 1, CLOCK.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retirarUnaDeSistemaFalla() {
        CategoriaMuro presentacion = CategoriaMuro.rehydrate("PRESENTACION", "Presentacion", "👋", 5, true,
                true, CLOCK.now(), CLOCK.now());
        assertThatThrownBy(() -> presentacion.actualizar(null, null, false, CLOCK.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renombrarUnaDeSistemaSiFunciona() {
        CategoriaMuro presentacion = CategoriaMuro.rehydrate("PRESENTACION", "Presentacion", "👋", 5, true,
                true, CLOCK.now(), CLOCK.now());
        presentacion.actualizar("Bienvenida", null, null, CLOCK.now());
        assertThat(presentacion.etiqueta()).isEqualTo("Bienvenida");
    }

    @Test
    void requireEliminableFallaParaDeSistema() {
        CategoriaMuro presentacion = CategoriaMuro.rehydrate("PRESENTACION", "Presentacion", "👋", 5, true,
                true, CLOCK.now(), CLOCK.now());
        assertThatThrownBy(presentacion::requireEliminable).isInstanceOf(IllegalArgumentException.class);
    }
}
