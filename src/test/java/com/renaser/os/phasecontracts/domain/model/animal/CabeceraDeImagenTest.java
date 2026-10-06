package com.renaser.os.phasecontracts.domain.model.animal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CabeceraDeImagenTest {

    private static byte[] webp(String chunk) {
        byte[] b = new byte[40];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, b, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, b, 8, 4);
        System.arraycopy(chunk.getBytes(StandardCharsets.US_ASCII), 0, b, 12, 4);
        return b;
    }

    @Test
    @DisplayName("PNG: ancho y alto salen del IHDR")
    void png() {
        byte[] b = new byte[33];
        int[] firma = {0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        for (int i = 0; i < 8; i++) b[i] = (byte) firma[i];
        b[18] = 0x03; b[19] = (byte) 0x84; // 900
        b[22] = 0x02; b[23] = 0x58;        // 600
        assertThat(CabeceraDeImagen.de(b)).isEqualTo(new CabeceraDeImagen("png", 900, 600));
    }

    @Test
    @DisplayName("WebP extendido (VP8X): 24 bits menos uno")
    void webpExtendido() {
        byte[] b = webp("VP8X");
        b[24] = (byte) 0xFF; b[25] = 0x03;          // 1024 - 1
        b[27] = (byte) 0xFF; b[28] = 0x01;          // 512 - 1
        assertThat(CabeceraDeImagen.de(b)).isEqualTo(new CabeceraDeImagen("webp", 1024, 512));
    }

    @Test
    @DisplayName("WebP con pérdida (VP8) y sin pérdida (VP8L)")
    void webpSimple() {
        byte[] con = webp("VP8 ");
        con[26] = 0x00; con[27] = 0x02; con[28] = 0x00; con[29] = 0x01; // 512 x 256
        assertThat(CabeceraDeImagen.de(con)).isEqualTo(new CabeceraDeImagen("webp", 512, 256));
        byte[] sin = webp("VP8L");
        sin[21] = (byte) 0xFF; sin[22] = 0x01; // ancho-1 = 511
        sin[23] = (byte) 0xC0; sin[24] = 0x03; // el resto de los bits es el alto
        CabeceraDeImagen c = CabeceraDeImagen.de(sin);
        assertThat(c.formato()).isEqualTo("webp");
        assertThat(c.ancho()).isEqualTo(512);
    }

    @Test
    @DisplayName("JPEG, texto o vacío no son imágenes de animal")
    void noSirve() {
        assertThatThrownBy(() -> CabeceraDeImagen.de(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 7}))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(ImagenDeAnimal.NO_ES_IMAGEN);
        assertThatThrownBy(() -> CabeceraDeImagen.de(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
