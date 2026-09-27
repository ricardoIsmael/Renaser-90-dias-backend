package com.renaser.os.community.infrastructure.adapter.out.imagen;

import com.renaser.os.community.domain.model.celula.FotoSubidaDelGrupo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La foto de un grupo preparada en el servidor (D-212): cuadrada, de 512 px, JPEG, y con memoria acotada. */
class FotoDelGrupoJava2dAdapterTest {

    private final FotoDelGrupoJava2dAdapter adapter = new FotoDelGrupoJava2dAdapter();

    @Test
    @DisplayName("una foto apaisada sale cuadrada de 512 px, en JPEG, recortada al centro")
    void apaisadaSaleCuadradaYRecortadaAlCentro() throws IOException {
        // 300×100: franjas laterales rojas de 100 px y el centro azul. El recorte al centro se queda con el azul.
        BufferedImage apaisada = new BufferedImage(300, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D lienzo = apaisada.createGraphics();
        lienzo.setColor(Color.RED);
        lienzo.fillRect(0, 0, 300, 100);
        lienzo.setColor(Color.BLUE);
        lienzo.fillRect(100, 0, 100, 100);
        lienzo.dispose();

        BufferedImage resultado = leer(adapter.comoJpegCuadrado(new FotoSubidaDelGrupo(png(apaisada), "image/png")));

        assertThat(resultado.getWidth()).isEqualTo(512);
        assertThat(resultado.getHeight()).isEqualTo(512);
        Color centro = new Color(resultado.getRGB(256, 256));
        assertThat(centro.getBlue()).as("el centro es el azul").isGreaterThan(200);
        assertThat(centro.getRed()).isLessThan(60);
    }

    @Test
    @DisplayName("sale como JPEG aunque haya llegado como PNG con transparencia (sobre blanco)")
    void siempreJpeg() throws IOException {
        BufferedImage transparente = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);

        byte[] jpeg = adapter.comoJpegCuadrado(new FotoSubidaDelGrupo(png(transparente), "image/png"));

        assertThat(jpeg).startsWith((byte) 0xFF, (byte) 0xD8);
        assertThat(new Color(leer(jpeg).getRGB(10, 10)).getRed()).as("la transparencia queda blanca").isGreaterThan(240);
    }

    @Test
    @DisplayName("una foto grande se lee submuestreada y sale igual de 512 px")
    void unaGrandeSeLeeSubmuestreada() throws IOException {
        BufferedImage grande = new BufferedImage(3000, 2000, BufferedImage.TYPE_INT_RGB);

        BufferedImage resultado = leer(adapter.comoJpegCuadrado(new FotoSubidaDelGrupo(png(grande), "image/png")));

        assertThat(resultado.getWidth()).isEqualTo(512);
    }

    @Test
    @DisplayName("algo que no es una imagen es 400, aunque diga ser JPEG")
    void loQueNoEsImagenEs400() {
        assertThatThrownBy(() -> adapter.comoJpegCuadrado(new FotoSubidaDelGrupo("no soy una foto".getBytes(), "image/jpeg")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No se pudo leer la foto");
    }

    @Test
    @DisplayName("medidas absurdas en la cabecera son 400 antes de leer un solo píxel")
    void medidasAbsurdasSon400() throws IOException {
        byte[] enorme = conMedidas(png(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)), 40_000, 40_000);

        assertThatThrownBy(() -> adapter.comoJpegCuadrado(new FotoSubidaDelGrupo(enorme, "image/png")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("medidas");
    }

    private static byte[] png(BufferedImage imagen) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", salida);
        return salida.toByteArray();
    }

    private static BufferedImage leer(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    /** Reescribe el ancho y el alto del IHDR de un PNG (y su CRC): la cabecera miente, los píxeles no cambian. */
    private static byte[] conMedidas(byte[] png, int ancho, int alto) {
        byte[] copia = png.clone();
        ByteBuffer buffer = ByteBuffer.wrap(copia);
        // Firma (8) + largo del chunk (4) + "IHDR" (4): el ancho empieza en 16 y el alto en 20.
        buffer.putInt(16, ancho);
        buffer.putInt(20, alto);
        CRC32 crc = new CRC32();
        crc.update(copia, 12, 17);
        buffer.putInt(29, (int) crc.getValue());
        return copia;
    }
}
