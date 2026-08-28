package com.renaser.os.onboarding.domain.model.media;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaOnboardingTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    private static UserId newUsuarioId() {
        return UserId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("registrar() crea con id null (lo asigna Postgres)")
    void registrarCreaConIdNulo() {
        UserId usuarioId = newUsuarioId();
        MediaOnboarding m = MediaOnboarding.registrar(usuarioId, "v90", "clave-1", ClaseMedia.AUDIO,
                MediaOnboarding.BUCKET_DEFAULT, "onboarding/" + usuarioId + "/audio/uuid", "audio/mpeg", 1024L, null,
                null, CLOCK);

        assertThat(m.id()).isNull();
        assertThat(m.clase()).isEqualTo(ClaseMedia.AUDIO);
        assertThat(m.creadoEn()).isEqualTo(CLOCK.now());
    }

    @Test
    @DisplayName("registrar() rechaza una ruta que no cae bajo el prefijo del propio usuario")
    void registrarRechazaRutaDeOtroUsuario() {
        UserId usuarioId = newUsuarioId();
        UserId otroUsuarioId = newUsuarioId();

        assertThatThrownBy(() -> MediaOnboarding.registrar(usuarioId, "v90", "clave-1", ClaseMedia.AUDIO,
                MediaOnboarding.BUCKET_DEFAULT, "onboarding/" + otroUsuarioId + "/audio/uuid", "audio/mpeg", 1024L,
                null, null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no corresponde al usuario");
    }

    @Test
    @DisplayName("rutaNueva() nunca es deterministica: dos llamadas dan rutas distintas")
    void rutaNuevaNoEsDeterministica() {
        UserId usuarioId = newUsuarioId();

        String rutaUno = MediaOnboarding.rutaNueva(usuarioId, ClaseMedia.AUDIO);
        String rutaDos = MediaOnboarding.rutaNueva(usuarioId, ClaseMedia.AUDIO);

        assertThat(rutaUno).isNotEqualTo(rutaDos);
        assertThat(rutaUno).startsWith("onboarding/" + usuarioId + "/audio/");
    }

    @Test
    @DisplayName("registrar() rechaza bucket/ruta vacios")
    void registrarValidaCamposObligatorios() {
        UserId usuarioId = newUsuarioId();
        assertThatThrownBy(() -> MediaOnboarding.registrar(usuarioId, null, null, ClaseMedia.FIRMA, " ", "ruta",
                null, null, null, null, CLOCK)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MediaOnboarding.registrar(usuarioId, null, null, ClaseMedia.FIRMA,
                MediaOnboarding.BUCKET_DEFAULT, " ", null, null, null, null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
