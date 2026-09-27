package com.renaser.os.chat.infrastructure.adapter.out.bienvenida;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Imágenes armadas en la prueba, para no depender de archivos: lisas, con franjas, en PNG, JPEG o GIF. */
final class ImagenesDePrueba {

    static final Color CELESTE = new Color(0xCF, 0xE8, 0xF5);

    private ImagenesDePrueba() {
    }

    static byte[] lisa(int ancho, int alto, Color color, String formato) {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        pintar(imagen, color, 0, alto);
        return codificar(imagen, formato);
    }

    /** Clara, con una franja de otro color entre {@code y0} e {@code y1}. */
    static byte[] conFranja(Color fondo, Color franja, int y0, int y1) {
        BufferedImage imagen = new BufferedImage(1200, 1200, BufferedImage.TYPE_INT_RGB);
        pintar(imagen, fondo, 0, 1200);
        pintar(imagen, franja, y0, y1);
        return codificar(imagen, "png");
    }

    static byte[] transparente(int lado) {
        return codificar(new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB), "png");
    }

    static byte[] fondoOriginal() {
        try (InputStream entrada = ImagenesDePrueba.class.getResourceAsStream("/bienvenida/fondo.png")) {
            return entrada.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static BufferedImage leer(byte[] contenido) {
        try {
            return ImageIO.read(new ByteArrayInputStream(contenido));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Si dos colores se parecen, con la tolerancia del JPEG. */
    static boolean parecido(int rgb, Color esperado) {
        return Math.abs(((rgb >> 16) & 0xFF) - esperado.getRed()) <= 12
                && Math.abs(((rgb >> 8) & 0xFF) - esperado.getGreen()) <= 12
                && Math.abs((rgb & 0xFF) - esperado.getBlue()) <= 12;
    }

    private static void pintar(BufferedImage imagen, Color color, int y0, int y1) {
        Graphics2D g = imagen.createGraphics();
        g.setColor(color);
        g.fillRect(0, y0, imagen.getWidth(), y1 - y0);
        g.dispose();
    }

    static byte[] codificar(BufferedImage imagen, String formato) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(imagen, formato, salida)) {
                throw new IllegalStateException("Sin escritor para " + formato);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return salida.toByteArray();
    }
}
