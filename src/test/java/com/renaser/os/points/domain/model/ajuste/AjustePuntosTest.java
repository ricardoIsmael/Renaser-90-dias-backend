package com.renaser.os.points.domain.model.ajuste;

import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AjustePuntosTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    private static UserId participante() {
        return UserId.of(UUID.randomUUID());
    }

    @Test
    void registrarCreaUnAsientoSinIdTodavia() {
        ResultadoAjuste resultado = new ResultadoAjuste(10, 10, 100, 110);

        AjustePuntos ajuste = AjustePuntos.registrar(participante(), MotivoPuntos.HABIT_COMPLETED, resultado,
                "nota", CLOCK);

        assertThat(ajuste.id()).isNull();
        assertThat(ajuste.delta()).isEqualTo(10);
        assertThat(ajuste.deltaAplicado()).isEqualTo(10);
        assertThat(ajuste.saldoPosterior()).isEqualTo(110);
        assertThat(ajuste.creadoEn()).isEqualTo(CLOCK.now());
    }

    @Test
    void saldoPosteriorNegativoEsInvalido() {
        assertThatThrownBy(() -> AjustePuntos.rehydrate(1L, participante(), MotivoPuntos.MANUAL_ADJUSTMENT, -10,
                -10, -1, null, CLOCK.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deltaFueraDeRangoSmallintEsInvalido() {
        assertThatThrownBy(() -> AjustePuntos.rehydrate(1L, participante(), MotivoPuntos.MANUAL_ADJUSTMENT,
                Short.MAX_VALUE + 1, 0, 0, null, CLOCK.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("los bordes exactos del rango smallint (Short.MIN_VALUE/MAX_VALUE) son validos")
    void deltaEnLosBordesExactosDelSmallintEsValido() {
        AjustePuntos enElTope = AjustePuntos.rehydrate(1L, participante(), MotivoPuntos.MANUAL_ADJUSTMENT,
                Short.MAX_VALUE, Short.MAX_VALUE, 0, null, CLOCK.now());
        AjustePuntos enElPiso = AjustePuntos.rehydrate(1L, participante(), MotivoPuntos.MANUAL_ADJUSTMENT,
                Short.MIN_VALUE, Short.MIN_VALUE, 0, null, CLOCK.now());

        assertThat(enElTope.delta()).isEqualTo(Short.MAX_VALUE);
        assertThat(enElPiso.delta()).isEqualTo(Short.MIN_VALUE);
    }

    @Test
    void deltaUnPuntoPorDebajoDelPisoSmallintEsInvalido() {
        assertThatThrownBy(() -> AjustePuntos.rehydrate(1L, participante(), MotivoPuntos.MANUAL_ADJUSTMENT,
                Short.MIN_VALUE - 1, 0, 0, null, CLOCK.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void participanteIdEsObligatorio() {
        assertThatThrownBy(() -> AjustePuntos.rehydrate(1L, null, MotivoPuntos.MANUAL_ADJUSTMENT, 1, 1, 1, null,
                CLOCK.now()))
                .isInstanceOf(NullPointerException.class);
    }
}
