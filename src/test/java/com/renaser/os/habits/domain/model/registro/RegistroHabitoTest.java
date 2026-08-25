package com.renaser.os.habits.domain.model.registro;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** service.ts: "FAILED and EXPIRED tracks cannot be completed or have evidence added" (linea 11). */
class RegistroHabitoTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    private static RegistroHabito nuevoPendiente() {
        return RegistroHabito.generar(UserId.of(UUID.randomUUID()), HabitoId.newId(), LocalDate.of(2026, 8, 24), 5,
                TipoDia.DISCIPLINA, false, CLOCK.now());
    }

    @Test
    void generarSiempreEmpiezaPendienteConCeroPuntos() {
        RegistroHabito r = nuevoPendiente();
        assertThat(r.estado()).isEqualTo(EstadoRegistro.PENDIENTE);
        assertThat(r.puntosOtorgados()).isZero();
        assertThat(r.completadoEn()).isNull();
    }

    @Test
    void diaProgramaFueraDeRangoEsInvalido() {
        assertThatThrownBy(() -> RegistroHabito.generar(UserId.of(UUID.randomUUID()), HabitoId.newId(),
                LocalDate.now(), 91, TipoDia.TODOS, false, CLOCK.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pendienteCompletarDirectoOk() {
        RegistroHabito r = nuevoPendiente();
        r.completar(10, "listo", null, null, CLOCK.now());
        assertThat(r.estado()).isEqualTo(EstadoRegistro.COMPLETADO);
        assertThat(r.puntosOtorgados()).isEqualTo(10);
        assertThat(r.completadoEn()).isEqualTo(CLOCK.now());
    }

    @Test
    void iniciarLuegoCompletarOk() {
        RegistroHabito r = nuevoPendiente();
        r.iniciar(CLOCK.now());
        assertThat(r.estado()).isEqualTo(EstadoRegistro.EN_CURSO);
        r.completar(3, null, null, null, CLOCK.now());
        assertThat(r.estado()).isEqualTo(EstadoRegistro.COMPLETADO);
    }

    @Test
    void completarConPuntosNegativosSeAcotaACero() {
        RegistroHabito r = nuevoPendiente();
        r.completar(-5, null, null, null, CLOCK.now());
        assertThat(r.puntosOtorgados()).isZero();
    }

    @Test
    void noSePuedeIniciarDosVeces() {
        RegistroHabito r = nuevoPendiente();
        r.iniciar(CLOCK.now());
        assertThatThrownBy(() -> r.iniciar(CLOCK.now())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expirarEsIdempotenteSobrePendiente() {
        RegistroHabito r = nuevoPendiente();
        r.expirar(CLOCK.now());
        assertThat(r.estado()).isEqualTo(EstadoRegistro.EXPIRADO);
        r.expirar(CLOCK.now()); // no explota, ya es terminal
        assertThat(r.estado()).isEqualTo(EstadoRegistro.EXPIRADO);
    }

    @Test
    void expiradoNuncaMasSeCompleta() {
        RegistroHabito r = nuevoPendiente();
        r.expirar(CLOCK.now());
        assertThatThrownBy(() -> r.completar(10, null, null, null, CLOCK.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void falladoNuncaMasSeCompleta() {
        RegistroHabito r = nuevoPendiente();
        r.iniciar(CLOCK.now());
        r.marcarFallido(CLOCK.now());
        assertThat(r.estado()).isEqualTo(EstadoRegistro.FALLIDO);
        assertThatThrownBy(() -> r.completar(10, null, null, null, CLOCK.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void marcarFallidoDesdePendienteTambienValido() {
        RegistroHabito r = nuevoPendiente();
        r.marcarFallido(CLOCK.now());
        assertThat(r.estado()).isEqualTo(EstadoRegistro.FALLIDO);
    }

    @Test
    @DisplayName("liberar() vuelve a PENDIENTE si es hoy, a EXPIRADO si no — y es no-op si no esta EN_CURSO (phoneFree.ts releaseTrack)")
    void liberarSegunSiEsHoy() {
        RegistroHabito r = nuevoPendiente();
        r.iniciar(CLOCK.now());
        r.liberar(true, CLOCK.now());
        assertThat(r.estado()).isEqualTo(EstadoRegistro.PENDIENTE);

        RegistroHabito r2 = nuevoPendiente();
        r2.iniciar(CLOCK.now());
        r2.liberar(false, CLOCK.now());
        assertThat(r2.estado()).isEqualTo(EstadoRegistro.EXPIRADO);

        RegistroHabito r3 = nuevoPendiente();
        r3.liberar(true, CLOCK.now()); // no estaba EN_CURSO: no-op
        assertThat(r3.estado()).isEqualTo(EstadoRegistro.PENDIENTE);
    }

    @Test
    void completarUnRegistroYaCompletadoFalla() {
        RegistroHabito r = nuevoPendiente();
        r.completar(10, null, null, null, CLOCK.now());
        assertThatThrownBy(() -> r.completar(5, null, null, null, CLOCK.now()))
                .isInstanceOf(IllegalStateException.class);
    }
}
