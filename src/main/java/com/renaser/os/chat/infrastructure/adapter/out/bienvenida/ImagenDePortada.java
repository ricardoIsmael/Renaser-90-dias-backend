package com.renaser.os.chat.infrastructure.adapter.out.bienvenida;

import com.renaser.os.chat.domain.model.bienvenida.PortadaDeBienvenida;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Abrir una portada subida y medir si el nombre se va a leer encima (D-210). Qué es aceptable lo decide
 * {@link PortadaDeBienvenida}; acá solo se abre la imagen y se mide.
 *
 * <p><b>Abrir sin arriesgar la memoria.</b> Las medidas se leen de la cabecera antes de decodificar, y se
 * decodifica salteando píxeles ({@code setSourceSubsampling}) para que el lado corto quede entre 1200 y
 * 2400: una foto de 8000 px no ocupa 190 MB en el proceso, ocupa unos 17. El contenedor de producción
 * tiene tope de memoria (V-8).
 *
 * <p><b>El lienzo.</b> Se usa el centro cuadrado de la imagen, llevado a {@link #LADO} × {@link #LADO}
 * (el tamaño de la exportación de Canva): así las medidas del nombre de {@link BienvenidaJava2dAdapter}
 * valen igual para cualquier portada. Lo transparente de un PNG queda blanco.
 *
 * <p><b>Dónde se tiene que leer el nombre.</b> La franja que puede ocupar el nombre más largo: a lo ancho
 * de {@link BienvenidaJava2dAdapter#ANCHO_MAXIMO} alrededor del centro, desde un poco por encima de las
 * mayúsculas (96 px, más el acento de una «Á») hasta apenas debajo de la línea base. Un punto es oscuro si
 * el verde de la letra no llega contra él a un contraste de 3:1 (WCAG, letra grande).
 */
final class ImagenDePortada {

    static final int LADO = 1200;

    private static final int FRANJA_X0 = Math.round(BienvenidaJava2dAdapter.CENTRO_X - BienvenidaJava2dAdapter.ANCHO_MAXIMO / 2);
    private static final int FRANJA_X1 = Math.round(BienvenidaJava2dAdapter.CENTRO_X + BienvenidaJava2dAdapter.ANCHO_MAXIMO / 2);
    private static final int FRANJA_Y0 = Math.round(BienvenidaJava2dAdapter.LINEA_BASE_Y - 96 - 24);
    private static final int FRANJA_Y1 = Math.round(BienvenidaJava2dAdapter.LINEA_BASE_Y + 8);
    /** Cada cuántos píxeles se mira: sobra para una franja de 1040 × 128 y es cuatro veces más rápido. */
    private static final int PASO = 2;
    private static final double CONTRASTE_MINIMO = 3.0;
    /** Por debajo de esta luminancia el verde de la letra no llega al contraste mínimo. */
    private static final double LUMINANCIA_MINIMA =
            CONTRASTE_MINIMO * (luminancia(BienvenidaJava2dAdapter.VERDE_RENASER.getRGB()) + 0.05) - 0.05;

    private ImagenDePortada() {
    }

    /**
     * Abre la imagen y la deja en el lienzo de la tarjeta.
     *
     * @throws IllegalArgumentException si no es una imagen que sirva ({@link PortadaDeBienvenida#exigirQueSePuedaUsar})
     *                                  o está dañada
     */
    static BufferedImage abrir(byte[] contenido) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
            ImageReader lector = lectorDe(entrada);
            try {
                lector.setInput(entrada, true, true);
                return aLienzo(leerRevisada(lector, contenido.length));
            } finally {
                lector.dispose();
            }
        } catch (IOException dañada) {
            throw new IllegalArgumentException("No se pudo abrir la imagen: puede estar dañada. Prueba con otra.", dañada);
        }
    }

    /** De 0 a 1: cuánto de la franja del nombre es demasiado oscuro para el verde de la letra. */
    static double parteOscuraDondeVaElNombre(BufferedImage lienzo) {
        int oscuros = 0;
        int mirados = 0;
        for (int y = FRANJA_Y0; y < FRANJA_Y1; y += PASO) {
            for (int x = FRANJA_X0; x < FRANJA_X1; x += PASO) {
                mirados++;
                if (luminancia(lienzo.getRGB(x, y)) < LUMINANCIA_MINIMA) {
                    oscuros++;
                }
            }
        }
        return (double) oscuros / mirados;
    }

    /** El lector que reconoce el contenido (por sus primeros bytes, no por lo que declaró quien subió). */
    private static ImageReader lectorDe(ImageInputStream entrada) {
        Iterator<ImageReader> lectores = entrada == null ? null : ImageIO.getImageReaders(entrada);
        if (lectores == null || !lectores.hasNext()) {
            throw new IllegalArgumentException(PortadaDeBienvenida.NO_ES_IMAGEN);
        }
        return lectores.next();
    }

    /** Revisa formato, peso y medidas con la cabecera, y recién entonces decodifica, salteando píxeles. */
    private static BufferedImage leerRevisada(ImageReader lector, long peso) throws IOException {
        int ancho = lector.getWidth(0);
        int alto = lector.getHeight(0);
        PortadaDeBienvenida.exigirQueSePuedaUsar(lector.getFormatName(), peso, ancho, alto);
        ImageReadParam parametros = lector.getDefaultReadParam();
        int salto = Math.max(1, Math.min(ancho, alto) / LADO);
        parametros.setSourceSubsampling(salto, salto, 0, 0);
        try {
            return lector.read(0, parametros);
        } catch (RuntimeException decodificador) {
            // Algunos decodificadores fallan con excepciones no declaradas ante un archivo roto.
            throw new IOException("El decodificador no pudo leer la imagen", decodificador);
        }
    }

    private static BufferedImage aLienzo(BufferedImage leida) {
        int lado = Math.min(leida.getWidth(), leida.getHeight());
        int x = (leida.getWidth() - lado) / 2;
        int y = (leida.getHeight() - lado) / 2;
        BufferedImage lienzo = new BufferedImage(LADO, LADO, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = lienzo.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, LADO, LADO);
            g.drawImage(leida, 0, 0, LADO, LADO, x, y, x + lado, y + lado, null);
        } finally {
            g.dispose();
        }
        return lienzo;
    }

    /** Luminancia relativa de WCAG 2 (sRGB lineal). */
    private static double luminancia(int rgb) {
        return 0.2126 * lineal((rgb >> 16) & 0xFF) + 0.7152 * lineal((rgb >> 8) & 0xFF) + 0.0722 * lineal(rgb & 0xFF);
    }

    private static double lineal(int canal) {
        double c = canal / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
