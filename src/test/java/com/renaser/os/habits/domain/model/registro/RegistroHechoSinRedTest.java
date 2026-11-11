package com.renaser.os.habits.domain.model.registro;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec 048 (FR-014): lo marcado sin red se completa aunque el barrido ya lo haya vencido; lo ya cerrado no se toca. */
class RegistroHechoSinRedTest {

    private static final Instant GENERADO = Instant.parse("2026-11-09T10:00:00Z");
    private static final Instant HECHO = Instant.parse("2026-11-10T02:10:00Z");

    private static RegistroHabito pendiente() {
        return RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), LocalDate.of(2026, 11, 9), 40, TipoDia.TODOS, false, GENERADO);
    }

    @Test
    @DisplayName("Un registro que el barrido venció se completa con la hora real y sus puntos")
    void completaUnoVencido() { // spec 048 FR-014
        RegistroHabito r = pendiente();
        r.expirar(Instant.parse("2026-11-10T06:00:00Z"));
        assertThat(r.estado()).isEqualTo(EstadoRegistro.EXPIRADO);

        r.completarHechoSinRed(10, "Lo hice en el campo", null, null, HECHO);

        assertThat(r.estado()).isEqualTo(EstadoRegistro.COMPLETADO);
        assertThat(r.puntosOtorgados()).isEqualTo(10);
        assertThat(r.completadoEn()).isEqualTo(HECHO);
    }

    @Test
    @DisplayName("Uno ya completado no se vuelve a completar")
    void unoCompletadoNoSeToca() {
        RegistroHabito r = pendiente();
        r.completar(10, null, null, null, GENERADO);

        assertThatThrownBy(() -> r.completarHechoSinRed(10, null, null, null, HECHO)).isInstanceOf(IllegalStateException.class);
    }
}
