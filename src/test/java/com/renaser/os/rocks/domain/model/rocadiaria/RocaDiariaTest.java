package com.renaser.os.rocks.domain.model.rocadiaria;

import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RocaDiariaTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    private static UserId participante() {
        return UserId.of(UUID.randomUUID());
    }

    private static RocaDiaria roca(int posicion) {
        return RocaDiaria.planificar(RocaDiariaId.of(UUID.randomUUID()), participante(),
                LocalDate.of(2026, 8, 25), posicion, "titulo", null, 5, false,
                EjeObjetivo.CUERPO, null, null, null, CLOCK);
    }

    @Test
    void laPosicionUnoEsSiempreVerde() {
        assertThat(roca(1).color()).isEqualTo(ColorPareto.VERDE);
    }

    @Test
    void laPosicionDosEsSiempreAmarilla() {
        assertThat(roca(2).color()).isEqualTo(ColorPareto.AMARILLA);
    }

    @Test
    void laPosicionTresEsSiempreRoja() {
        assertThat(roca(3).color()).isEqualTo(ColorPareto.ROJA);
    }

    @Test
    void posicionInvalidaRechazada() {
        assertThatThrownBy(() -> roca(4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void completarUnaVezFunciona() {
        RocaDiaria r = roca(1);
        r.completar(CLOCK.now(), CLOCK);
        assertThat(r.completada()).isTrue();
        assertThat(r.completadaEn()).isEqualTo(CLOCK.now());
    }

    @Test
    void completarDosVecesFalla() {
        RocaDiaria r = roca(1);
        r.completar(CLOCK.now(), CLOCK);
        assertThatThrownBy(() -> r.completar(CLOCK.now(), CLOCK)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void otorgarPuntosSoloUnaVez() {
        RocaDiaria r = roca(1);
        assertThat(r.puedeOtorgarPuntos()).isTrue();
        r.otorgarPuntos(10);
        assertThat(r.puedeOtorgarPuntos()).isFalse();
    }

    @Test
    void verdeNuncaEstaBloqueada() {
        assertThat(RocaDiaria.bloqueadaPorPareto(ColorPareto.VERDE, false)).isFalse();
        assertThat(RocaDiaria.bloqueadaPorPareto(ColorPareto.VERDE, true)).isFalse();
    }

    @Test
    void amarillaYRojaBloqueadasSinVerdeCompletada() {
        assertThat(RocaDiaria.bloqueadaPorPareto(ColorPareto.AMARILLA, false)).isTrue();
        assertThat(RocaDiaria.bloqueadaPorPareto(ColorPareto.ROJA, false)).isTrue();
    }

    @Test
    void amarillaYRojaDesbloqueadasConVerdeCompletada() {
        assertThat(RocaDiaria.bloqueadaPorPareto(ColorPareto.AMARILLA, true)).isFalse();
        assertThat(RocaDiaria.bloqueadaPorPareto(ColorPareto.ROJA, true)).isFalse();
    }

    @Test
    void puntajeImpactoFueraDeRangoEsInvalido() {
        assertThatThrownBy(() -> RocaDiaria.planificar(RocaDiariaId.of(UUID.randomUUID()), participante(),
                LocalDate.now(), 1, "t", null, 11, false,
                EjeObjetivo.CUERPO, null, null, null, CLOCK)).isInstanceOf(IllegalArgumentException.class);
    }
}
