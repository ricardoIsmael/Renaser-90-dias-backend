package com.renaser.os.habits.domain.model.desbloqueo;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** D-196: al retroceder el dia, lo que ya estuvo activo sigue activo; lo que no llego, espera. */
class DesbloqueoHabitoRetrocesoTest {

    private static final Instant AHORA = Instant.parse("2026-09-26T15:00:00Z");

    private static DesbloqueoHabito desbloqueadoEl(int dia) {
        return DesbloqueoHabito.rehydrate(UserId.of(UUID.randomUUID()), HabitoId.of(UUID.randomUUID()), dia,
                AHORA, AHORA, AHORA);
    }

    @Test
    void siSuDiaYaLlegoLeToca() {
        assertThat(desbloqueadoEl(30).todaviaNoLeToca(30, null)).isFalse();
        assertThat(desbloqueadoEl(30).todaviaNoLeToca(45, null)).isFalse();
    }

    @Test
    void siSuDiaNoLlegoYNuncaCorrioTodaviaNoLeToca() {
        assertThat(desbloqueadoEl(30).todaviaNoLeToca(25, null)).isTrue();
    }

    @Test
    void siYaCorrioDesdeSuDiaSigueAunqueSeLoRetroceda() {
        assertThat(desbloqueadoEl(30).todaviaNoLeToca(25, 30)).isFalse();
        assertThat(desbloqueadoEl(30).todaviaNoLeToca(25, 33)).isFalse();
    }

    @Test
    void registrosAnterioresASuDiaNoCuentanComoActivacion() {
        assertThat(desbloqueadoEl(30).todaviaNoLeToca(25, 29)).isTrue();
    }
}
