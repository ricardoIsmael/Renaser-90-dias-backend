package com.renaser.os.onboarding.domain.model.estado;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EstadoOnboardingTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    private static UserId newUsuarioId() {
        return UserId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("iniciar() crea una fila nueva, sin completar, con timestamps de arranque")
    void iniciarCreaFilaNueva() {
        UserId usuarioId = newUsuarioId();

        EstadoOnboarding estado = EstadoOnboarding.iniciar(usuarioId, CLOCK);

        assertThat(estado.usuarioId()).isEqualTo(usuarioId);
        assertThat(estado.completado()).isFalse();
        assertThat(estado.completadoEn()).isNull();
        assertThat(estado.iniciadoEn()).isEqualTo(CLOCK.now());
        assertThat(estado.ultimaActividadEn()).isEqualTo(CLOCK.now());
        assertThat(estado.terminosAceptadosEn()).isNull();
        assertThat(estado.pactoAceptadoEn()).isNull();
        assertThat(estado.pactoFirmadoEn()).isNull();
        assertThat(estado.rocasSyncAceptadoEn()).isNull();
    }

    @Test
    @DisplayName("avanzar() solo pisa los campos no nulos, deja el resto igual")
    void avanzarSoloPisaLoQueLlega() {
        EstadoOnboarding estado = EstadoOnboarding.iniciar(newUsuarioId(), CLOCK);
        estado.avanzar("v90", "seccion-1", 2, "{\"paso\":2}", CLOCK);

        FixedClock masTarde = FixedClock.at(CLOCK.now().plusSeconds(60));
        estado.avanzar(null, "seccion-2", null, null, masTarde);

        assertThat(estado.flujoActual()).isEqualTo("v90");
        assertThat(estado.seccionActual()).isEqualTo("seccion-2");
        assertThat(estado.pasoActual()).isEqualTo(2);
        assertThat(estado.progresoFlujo()).isEqualTo("{\"paso\":2}");
        assertThat(estado.ultimaActividadEn()).isEqualTo(masTarde.now());
    }

    @ParameterizedTest
    @EnumSource(HitoOnboarding.class)
    @DisplayName("aceptarHito() setea el timestamp del hito correspondiente y ninguno mas")
    void aceptarHitoSeteaSoloElHitoCorrespondiente(HitoOnboarding hito) {
        EstadoOnboarding estado = EstadoOnboarding.iniciar(newUsuarioId(), CLOCK);

        estado.aceptarHito(hito, CLOCK);

        assertThat(timestampDe(estado, hito)).isEqualTo(CLOCK.now());
        for (HitoOnboarding otro : HitoOnboarding.values()) {
            if (otro != hito) {
                assertThat(timestampDe(estado, otro)).isNull();
            }
        }
    }

    @Test
    @DisplayName("marcarCompletado() es idempotente: la segunda llamada no mueve completadoEn")
    void marcarCompletadoEsIdempotente() {
        EstadoOnboarding estado = EstadoOnboarding.iniciar(newUsuarioId(), CLOCK);

        estado.marcarCompletado(CLOCK);
        Instant primeraVez = estado.completadoEn();

        FixedClock masTarde = FixedClock.at(CLOCK.now().plusSeconds(3600));
        estado.marcarCompletado(masTarde);

        assertThat(estado.completado()).isTrue();
        assertThat(estado.completadoEn()).isEqualTo(primeraVez);
    }

    @Test
    @DisplayName("aceptarHito() rechaza hito null")
    void aceptarHitoRechazaNull() {
        EstadoOnboarding estado = EstadoOnboarding.iniciar(newUsuarioId(), CLOCK);

        assertThatThrownBy(() -> estado.aceptarHito(null, CLOCK)).isInstanceOf(NullPointerException.class);
    }

    private static Instant timestampDe(EstadoOnboarding estado, HitoOnboarding hito) {
        return switch (hito) {
            case TERMINOS -> estado.terminosAceptadosEn();
            case PACTO -> estado.pactoAceptadoEn();
            case PACTO_FIRMADO -> estado.pactoFirmadoEn();
            case ROCAS_SYNC -> estado.rocasSyncAceptadoEn();
        };
    }
}
