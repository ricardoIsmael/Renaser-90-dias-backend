package com.renaser.os.community.domain.model.testimonio;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestimonioTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
    /** El id ya no lo sortea la factoria: entra por parametro, generado por el puerto IdGenerator. */
    private static final TestimonioId ID = TestimonioId.of(UUID.randomUUID());

    @Test
    void crearSiempreNaceDestacado() {
        Testimonio t = Testimonio.crear(ID, UserId.of(UUID.randomUUID()), null, "Ana", null, null, null,
                "Cambio mi vida", 5, CLOCK.now());
        assertThat(t.id()).isEqualTo(ID);
        assertThat(t.destacado()).isTrue();
    }

    @Test
    void rolVacioCaeAlDefault() {
        Testimonio t = Testimonio.crear(ID, null, null, "Ana", "  ", null, null, "Cambio mi vida", 5, CLOCK.now());
        assertThat(t.rolTexto()).isEqualTo("Miembro de la comunidad");
    }

    @Test
    void nombreCortoEsInvalido() {
        assertThatThrownBy(() -> Testimonio.crear(ID, null, null, "A", null, null, null, "Cambio mi vida", 5,
                CLOCK.now())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void estrellasFueraDeRangoEsInvalido() {
        assertThatThrownBy(() -> Testimonio.crear(ID, null, null, "Ana", null, null, null, "Cambio mi vida", 6,
                CLOCK.now())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retirarYDestacarCambianElFlag() {
        Testimonio t = Testimonio.crear(ID, null, null, "Ana", null, null, null, "Cambio mi vida", 5, CLOCK.now());
        t.retirar();
        assertThat(t.destacado()).isFalse();
        t.destacar();
        assertThat(t.destacado()).isTrue();
    }
}
