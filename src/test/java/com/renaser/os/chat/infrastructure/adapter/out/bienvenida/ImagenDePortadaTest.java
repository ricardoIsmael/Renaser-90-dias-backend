package com.renaser.os.chat.infrastructure.adapter.out.bienvenida;

import com.renaser.os.chat.domain.model.bienvenida.PortadaDeBienvenida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Random;

import static com.renaser.os.chat.infrastructure.adapter.out.bienvenida.ImagenesDePrueba.conFranja;
import static com.renaser.os.chat.infrastructure.adapter.out.bienvenida.ImagenesDePrueba.lisa;
import static com.renaser.os.chat.infrastructure.adapter.out.bienvenida.ImagenesDePrueba.parecido;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Abrir una portada subida y medir si el nombre se lee encima (D-210). La calibración está en la primera
 * prueba: la portada de Operaciones, la que ya se usa, tiene que pasar holgada.
 */
class ImagenDePortadaTest {

    @Test
    @DisplayName("la portada de Operaciones pasa: su franja del nombre es clara")
    void laOriginalPasa() {
        BufferedImage lienzo = ImagenDePortada.abrir(ImagenesDePrueba.fondoOriginal());

        assertThat(lienzo.getWidth()).isEqualTo(1200);
        assertThat(lienzo.getHeight()).isEqualTo(1200);
        assertThat(ImagenDePortada.parteOscuraDondeVaElNombre(lienzo)).isLessThan(0.01);
    }

    @Test
    @DisplayName("una portada negra no pasa: el verde del nombre no se vería")
    void unaOscuraNoPasa() {
        double oscura = ImagenDePortada.parteOscuraDondeVaElNombre(ImagenDePortada.abrir(lisa(1200, 1200, Color.BLACK, "jpeg")));

        assertThat(oscura).isGreaterThan(0.99);
        assertThatThrownBy(() -> PortadaDeBienvenida.exigirNombreLegible(oscura))
                .hasMessageContaining("El nombre no se leería");
    }

    @Test
    @DisplayName("lo oscuro FUERA de la franja del nombre no importa; lo oscuro DENTRO, sí")
    void importaSoloLaFranjaDelNombre() {
        assertThat(ImagenDePortada.parteOscuraDondeVaElNombre(ImagenDePortada.abrir(
                conFranja(Color.WHITE, Color.BLACK, 0, 700)))).isZero();
        assertThat(ImagenDePortada.parteOscuraDondeVaElNombre(ImagenDePortada.abrir(
                conFranja(Color.WHITE, Color.BLACK, 760, 880)))).isGreaterThan(0.9);
    }

    @Test
    @DisplayName("el límite es el contraste 3:1 con el verde de la letra: gris #808080 pasa, #707070 no")
    void elLimiteEsElContraste() {
        assertThat(ImagenDePortada.parteOscuraDondeVaElNombre(ImagenDePortada.abrir(
                lisa(1200, 1200, new Color(0x80, 0x80, 0x80), "png")))).isZero();
        assertThat(ImagenDePortada.parteOscuraDondeVaElNombre(ImagenDePortada.abrir(
                lisa(1200, 1200, new Color(0x70, 0x70, 0x70), "png")))).isEqualTo(1.0);
    }

    @Test
    @DisplayName("si no es cuadrada se usa el centro, llevado al lienzo de 1200 × 1200")
    void noCuadradaSeUsaElCentro() {
        BufferedImage apaisada = new BufferedImage(1800, 1200, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 1800; x++) {
            Color color = x < 300 ? Color.RED : x >= 1500 ? Color.BLUE : ImagenesDePrueba.CELESTE;
            for (int y = 0; y < 1200; y++) {
                apaisada.setRGB(x, y, color.getRGB());
            }
        }

        BufferedImage lienzo = ImagenDePortada.abrir(ImagenesDePrueba.codificar(apaisada, "png"));

        assertThat(lienzo.getWidth()).isEqualTo(1200);
        for (int x : new int[] {2, 600, 1197}) {
            assertThat(parecido(lienzo.getRGB(x, 600), ImagenesDePrueba.CELESTE)).as("x=%d", x).isTrue();
        }
    }

    @Test
    @DisplayName("una foto grande se abre salteando píxeles y queda en 1200 × 1200")
    void unaGrandeSeAbre() {
        BufferedImage lienzo = ImagenDePortada.abrir(lisa(4000, 3000, ImagenesDePrueba.CELESTE, "jpeg"));

        assertThat(lienzo.getWidth()).isEqualTo(1200);
        assertThat(lienzo.getHeight()).isEqualTo(1200);
        assertThat(parecido(lienzo.getRGB(600, 600), ImagenesDePrueba.CELESTE)).isTrue();
    }

    @Test
    @DisplayName("lo transparente de un PNG queda blanco, no negro")
    void transparenteQuedaBlanco() {
        BufferedImage lienzo = ImagenDePortada.abrir(ImagenesDePrueba.transparente(1200));

        assertThat(parecido(lienzo.getRGB(600, 820), Color.WHITE)).isTrue();
        assertThat(ImagenDePortada.parteOscuraDondeVaElNombre(lienzo)).isZero();
    }

    @Test
    @DisplayName("se rechaza lo que no es JPG ni PNG (por el contenido), lo chico y lo dañado")
    void loQueNoSirve() {
        assertThatThrownBy(() -> ImagenDePortada.abrir(lisa(1200, 1200, Color.WHITE, "gif")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Esa imagen no es JPG ni PNG.");
        byte[] basura = new byte[4096];
        new Random(7).nextBytes(basura);
        assertThatThrownBy(() -> ImagenDePortada.abrir(basura))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Esa imagen no es JPG ni PNG.");
        assertThatThrownBy(() -> ImagenDePortada.abrir(lisa(500, 500, Color.WHITE, "png")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("muy chica (500 × 500 px)");
        byte[] png = conFranja(Color.WHITE, Color.BLACK, 100, 1100);
        assertThatThrownBy(() -> ImagenDePortada.abrir(Arrays.copyOf(png, png.length / 2)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("puede estar dañada");
    }
}
