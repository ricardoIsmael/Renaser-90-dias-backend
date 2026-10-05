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

        String rutaUno = MediaOnboarding.rutaNueva(UUID.randomUUID(), usuarioId, ClaseMedia.AUDIO);
        String rutaDos = MediaOnboarding.rutaNueva(UUID.randomUUID(), usuarioId, ClaseMedia.AUDIO);

        assertThat(rutaUno).isNotEqualTo(rutaDos);
        assertThat(rutaUno).startsWith("onboarding/" + usuarioId + "/audio/");
    }

    @Test
    @DisplayName("esDe(): solo es de quien lo subió (D-253: la condición para firmarle la lectura)")
    void esDeSoloQuienLoSubio() {
        UserId duena = newUsuarioId();
        MediaOnboarding firma = MediaOnboarding.registrar(duena, "pacto", "signature", ClaseMedia.FIRMA,
                MediaOnboarding.BUCKET_DEFAULT, "onboarding/" + duena + "/firma/uuid", "image/png", null, null, null,
                CLOCK);

        assertThat(firma.esDe(duena)).isTrue();
        assertThat(firma.esDe(UserId.of(UUID.fromString(duena.toString())))).as("por valor, no por instancia").isTrue();
        assertThat(firma.esDe(newUsuarioId())).isFalse();
    }

    @Test
    @DisplayName("esFirma(): solo la clase FIRMA; un audio o una foto no se muestran como firma")
    void esFirmaSoloLaClaseFirma() {
        UserId usuarioId = newUsuarioId();

        assertThat(media(usuarioId, ClaseMedia.FIRMA).esFirma()).isTrue();
        assertThat(media(usuarioId, ClaseMedia.AUDIO).esFirma()).isFalse();
        assertThat(media(usuarioId, ClaseMedia.FOTO).esFirma()).isFalse();
        assertThat(media(usuarioId, ClaseMedia.DOCUMENTO).esFirma()).isFalse();
    }

    private static MediaOnboarding media(UserId usuarioId, ClaseMedia clase) {
        return MediaOnboarding.registrar(usuarioId, "pacto", "signature", clase, MediaOnboarding.BUCKET_DEFAULT,
                "onboarding/" + usuarioId + "/" + clase.name().toLowerCase() + "/uuid", null, null, null, null, CLOCK);
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
