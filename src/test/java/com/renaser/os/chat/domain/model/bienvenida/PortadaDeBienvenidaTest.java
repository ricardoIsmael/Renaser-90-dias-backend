package com.renaser.os.chat.domain.model.bienvenida;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Qué imagen sirve de portada para la tarjeta de bienvenida (D-210). Sin Spring. */
class PortadaDeBienvenidaTest {

    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg", "image/png", "IMAGE/PNG", " image/jpeg "})
    @DisplayName("se sube JPEG o PNG")
    void tiposQueSeSuben(String tipo) {
        assertThatCode(() -> PortadaDeBienvenida.exigirTipoDeContenido(tipo)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"image/gif", "image/webp", "application/pdf", ""})
    @DisplayName("otro tipo no")
    void otrosTiposNo(String tipo) {
        assertThatThrownBy(() -> PortadaDeBienvenida.exigirTipoDeContenido(tipo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La portada tiene que ser una imagen JPG o PNG.");
    }

    @Test
    @DisplayName("solo rutas de portadas: nunca la de otro objeto del depósito")
    void soloRutasDePortadas() {
        assertThat(PortadaDeBienvenida.exigirRutaPropia("bienvenida/portadas/0b8c7f1e")).isEqualTo("bienvenida/portadas/0b8c7f1e");
        for (String ajena : new String[] {null, "", "bienvenida/portadas/", "evidencia-habitos/ana/1/2",
                "firmas/ana/pacto.png", "bienvenida/portadas/../../firmas/x", "bienvenida/portadas//x",
                "chat/bienvenida/portadas/x", "bienvenida/portadas/" + "x".repeat(300)}) {
            assertThatThrownBy(() -> PortadaDeBienvenida.exigirRutaPropia(ajena)).as(String.valueOf(ajena))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("formato por el contenido, peso y medidas, con el motivo en palabras simples")
    void formatoPesoYMedidas() {
        assertThatCode(() -> PortadaDeBienvenida.exigirQueSePuedaUsar("JPEG", 400_000, 1200, 1200)).doesNotThrowAnyException();
        assertThatCode(() -> PortadaDeBienvenida.exigirQueSePuedaUsar("png", 1_218_629, 600, 8000)).doesNotThrowAnyException();

        assertThatThrownBy(() -> PortadaDeBienvenida.exigirQueSePuedaUsar("gif", 1000, 1200, 1200))
                .hasMessage("Esa imagen no es JPG ni PNG.");
        assertThatThrownBy(() -> PortadaDeBienvenida.exigirQueSePuedaUsar(null, 1000, 1200, 1200))
                .hasMessage("Esa imagen no es JPG ni PNG.");
        assertThatThrownBy(() -> PortadaDeBienvenida.exigirQueSePuedaUsar("jpeg", 7_549_747, 1200, 1200))
                .hasMessage("La imagen pesa 7,2 MB: el máximo es 5 MB.");
        assertThatThrownBy(() -> PortadaDeBienvenida.exigirQueSePuedaUsar("jpeg", 1000, 599, 1200))
                .hasMessage("La imagen es muy chica (599 × 1200 px): tiene que medir al menos 600 px por lado.");
        assertThatThrownBy(() -> PortadaDeBienvenida.exigirQueSePuedaUsar("jpeg", 1000, 8001, 1200))
                .hasMessage("La imagen es demasiado grande (8001 × 1200 px): el máximo es 8000 px por lado.");
    }

    @Test
    @DisplayName("el nombre se tiene que leer: hasta un 10 % de la franja puede ser oscura")
    void nombreLegible() {
        assertThatCode(() -> PortadaDeBienvenida.exigirNombreLegible(0.0)).doesNotThrowAnyException();
        assertThatCode(() -> PortadaDeBienvenida.exigirNombreLegible(0.10)).doesNotThrowAnyException();
        assertThatThrownBy(() -> PortadaDeBienvenida.exigirNombreLegible(0.11))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El nombre no se leería");
    }
}
