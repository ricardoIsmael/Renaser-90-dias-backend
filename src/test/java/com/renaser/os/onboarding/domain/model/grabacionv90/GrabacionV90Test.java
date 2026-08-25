package com.renaser.os.onboarding.domain.model.grabacionv90;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrabacionV90Test {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    private static UserId newUsuarioId() {
        return UserId.of(UUID.randomUUID());
    }

    private static GrabacionV90 grabada() {
        GrabacionV90 g = GrabacionV90.crearSlot(newUsuarioId(), "FASE_1", "MENTE", (short) 0, "v90_mente_0", CLOCK);
        g.marcarGrabada(1L, BigDecimal.valueOf(45), "transcripcion", CLOCK);
        return g;
    }

    @Test
    @DisplayName("crearSlot(): placeholder PENDIENTE, sin audio, 0 intentos")
    void crearSlotEsPlaceholder() {
        GrabacionV90 g = GrabacionV90.crearSlot(newUsuarioId(), "FASE_1", "MENTE", (short) 0, "v90_mente_0", CLOCK);

        assertThat(g.grabada()).isFalse();
        assertThat(g.estadoIa()).isEqualTo(EstadoIAv90.PENDIENTE);
        assertThat(g.intentosIa()).isZero();
    }

    @Test
    @DisplayName("procesarIntentoDeValidacion() sin audio grabado explota")
    void procesarSinAudioExplota() {
        GrabacionV90 g = GrabacionV90.crearSlot(newUsuarioId(), "FASE_1", "MENTE", (short) 0, "v90_mente_0", CLOCK);

        assertThatThrownBy(() -> g.procesarIntentoDeValidacion(CLOCK)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("3 intentos sin resultado seguidos -> REVISION_MANUAL")
    void tresIntentosSinResultadoCaeARevisionManual() {
        GrabacionV90 g = grabada();

        g.procesarIntentoDeValidacion(CLOCK);
        g.registrarSinResultado(CLOCK);
        assertThat(g.estadoIa()).isEqualTo(EstadoIAv90.PENDIENTE);
        assertThat(g.intentosIa()).isEqualTo((short) 1);

        g.procesarIntentoDeValidacion(CLOCK);
        g.registrarSinResultado(CLOCK);
        assertThat(g.estadoIa()).isEqualTo(EstadoIAv90.PENDIENTE);
        assertThat(g.intentosIa()).isEqualTo((short) 2);

        g.procesarIntentoDeValidacion(CLOCK);
        g.registrarSinResultado(CLOCK);
        assertThat(g.estadoIa()).isEqualTo(EstadoIAv90.REVISION_MANUAL);
        assertThat(g.intentosIa()).isEqualTo((short) 3);
    }

    @Test
    @DisplayName("procesarIntentoDeValidacion() agotados los 3 intentos explota (no se puede reintentar mas)")
    void agotadosLosIntentosNoReintenta() {
        GrabacionV90 g = grabada();
        for (int i = 0; i < 3; i++) {
            g.procesarIntentoDeValidacion(CLOCK);
            g.registrarSinResultado(CLOCK);
        }

        assertThatThrownBy(() -> g.procesarIntentoDeValidacion(CLOCK)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("registrarAprobacion() deja el veredicto final APROBADA")
    void registrarAprobacionEsFinal() {
        GrabacionV90 g = grabada();
        g.procesarIntentoDeValidacion(CLOCK);

        g.registrarAprobacion("{\"nota\":10}", CLOCK);

        assertThat(g.estadoIa()).isEqualTo(EstadoIAv90.APROBADA);
        assertThat(g.feedbackIa()).isEqualTo("{\"nota\":10}");
        assertThatThrownBy(() -> g.procesarIntentoDeValidacion(CLOCK)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("registrarRechazo() deja el veredicto final RECHAZADA")
    void registrarRechazoEsFinal() {
        GrabacionV90 g = grabada();
        g.procesarIntentoDeValidacion(CLOCK);

        g.registrarRechazo("{\"motivo\":\"audio incompleto\"}", CLOCK);

        assertThat(g.estadoIa()).isEqualTo(EstadoIAv90.RECHAZADA);
        assertThatThrownBy(() -> g.procesarIntentoDeValidacion(CLOCK)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("marcarGrabada() (re-grabado) reinicia el veredicto de IA a PENDIENTE con 0 intentos")
    void reGrabarReiniciaElVeredicto() {
        GrabacionV90 g = grabada();
        g.procesarIntentoDeValidacion(CLOCK);
        g.registrarRechazo("mal", CLOCK);

        g.marcarGrabada(2L, BigDecimal.valueOf(50), "nueva transcripcion", CLOCK);

        assertThat(g.estadoIa()).isEqualTo(EstadoIAv90.PENDIENTE);
        assertThat(g.intentosIa()).isZero();
        assertThat(g.feedbackIa()).isNull();
        assertThat(g.mediaId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("crearSlot() rechaza indice negativo, fase/eje vacios")
    void crearSlotValidaCampos() {
        UserId usuarioId = newUsuarioId();
        assertThatThrownBy(() -> GrabacionV90.crearSlot(usuarioId, "", "MENTE", (short) 0, null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GrabacionV90.crearSlot(usuarioId, "FASE_1", " ", (short) 0, null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GrabacionV90.crearSlot(usuarioId, "FASE_1", "MENTE", (short) -1, null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("E-37: procesarIntentoDeValidacion() no puede reentrar mientras ya hay un intento PROCESANDO "
            + "(bloquea el doble despacho async que pisaba un veredicto)")
    void procesarIntentoDeValidacionRechazaReentradaEnProcesando() {
        GrabacionV90 g = grabada();
        g.procesarIntentoDeValidacion(CLOCK);

        assertThatThrownBy(() -> g.procesarIntentoDeValidacion(CLOCK)).isInstanceOf(IllegalStateException.class);
        assertThat(g.intentosIa()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("E-37: registrarAprobacion()/registrarRechazo()/registrarSinResultado() exigen PROCESANDO — "
            + "no se puede resolver un intento que no existe")
    void resolverSinIntentoEnCursoExplota() {
        GrabacionV90 g = grabada();

        assertThatThrownBy(() -> g.registrarAprobacion("{}", CLOCK)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> g.registrarRechazo("{}", CLOCK)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> g.registrarSinResultado(CLOCK)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("E-37: un veredicto ya final (APROBADA) no se puede sobrescribir con un segundo "
            + "registrarSinResultado/registrarRechazo tardio (doble despacho async)")
    void veredictoFinalNoSePisaConUnSegundoDespacho() {
        GrabacionV90 g = grabada();
        g.procesarIntentoDeValidacion(CLOCK);
        g.registrarAprobacion("{\"nota\":10}", CLOCK);

        assertThatThrownBy(() -> g.registrarSinResultado(CLOCK)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> g.registrarRechazo("tardio", CLOCK)).isInstanceOf(IllegalStateException.class);
        assertThat(g.estadoIa()).isEqualTo(EstadoIAv90.APROBADA);
    }
}
