package com.renaser.os.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** N-3: qué tipos y qué pesos admite cada uso de las subidas prefirmadas. */
class TipoDeSubidaTest {

    @ParameterizedTest
    @ValueSource(strings = {"text/html", "image/svg+xml", "application/javascript", "application/x-msdownload",
            "text/html; charset=utf-8", "IMAGE/SVG+XML", "", "image/gif"})
    @DisplayName("la evidencia y el avatar rechazan lo que un navegador ejecuta y lo que la app no manda")
    void rechazaLoQueNoEsMedia(String tipo) {
        assertThatThrownBy(() -> TipoDeSubida.EVIDENCIA.exigirTipoPermitido(tipo))
                .isInstanceOfSatisfying(RechazoConCodigoException.class, e -> {
                    assertThat(e.estado()).isEqualTo(400);
                    assertThat(e.codigo()).isEqualTo("TIPO_DE_ARCHIVO_NO_PERMITIDO");
                });
        assertThatThrownBy(() -> TipoDeSubida.AVATAR.exigirTipoPermitido(tipo))
                .isInstanceOf(RechazoConCodigoException.class);
    }

    @Test
    @DisplayName("un tipo nulo se rechaza, no revienta")
    void nuloSeRechaza() {
        assertThatThrownBy(() -> TipoDeSubida.EVIDENCIA.exigirTipoPermitido(null))
                .isInstanceOf(RechazoConCodigoException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg", "video/mp4", "video/quicktime", "video/3gpp", "video/webm", "audio/m4a",
            "audio/3gpp", "audio/x-caf", "audio/wav", "Image/JPEG", "audio/mp4; codecs=mp4a.40.2"})
    @DisplayName("la evidencia admite todo lo que la app captura hoy")
    void evidenciaAdmiteLoQueLaAppMandaHoy(String tipo) {
        assertThatCode(() -> TipoDeSubida.EVIDENCIA.exigirTipoPermitido(tipo)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el avatar solo admite imágenes de mapa de bits: ni video, ni audio, ni svg")
    void avatarSoloImagenes() {
        assertThat(TipoDeSubida.AVATAR.permite("image/jpeg")).isTrue();
        assertThat(TipoDeSubida.AVATAR.permite("image/png")).isTrue();
        assertThat(TipoDeSubida.AVATAR.permite("image/webp")).isTrue();
        assertThat(TipoDeSubida.AVATAR.permite("image/heic")).isTrue();
        assertThat(TipoDeSubida.AVATAR.permite("video/mp4")).isFalse();
        assertThat(TipoDeSubida.AVATAR.permite("audio/m4a")).isFalse();
        assertThat(TipoDeSubida.AVATAR.permite("image/svg+xml")).isFalse();
    }

    @Test
    @DisplayName("el peso que pasa del tope de su tipo se rechaza con ARCHIVO_DEMASIADO_GRANDE")
    void topeDePeso() {
        long mb = 1024L * 1024L;
        assertThatCode(() -> TipoDeSubida.EVIDENCIA.exigirPeso(300 * mb, "video/mp4")).doesNotThrowAnyException();
        assertThatThrownBy(() -> TipoDeSubida.EVIDENCIA.exigirPeso(300 * mb + 1, "video/mp4"))
                .isInstanceOfSatisfying(RechazoConCodigoException.class,
                        e -> assertThat(e.codigo()).isEqualTo("ARCHIVO_DEMASIADO_GRANDE"));
        assertThatThrownBy(() -> TipoDeSubida.EVIDENCIA.exigirPeso(21 * mb, "image/jpeg"))
                .isInstanceOf(RechazoConCodigoException.class);
        assertThatThrownBy(() -> TipoDeSubida.AVATAR.exigirPeso(11 * mb, "image/jpeg"))
                .isInstanceOf(RechazoConCodigoException.class);
    }
}
