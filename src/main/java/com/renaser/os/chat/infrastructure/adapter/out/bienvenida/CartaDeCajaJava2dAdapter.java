package com.renaser.os.chat.infrastructure.adapter.out.bienvenida;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.renaser.os.chat.application.ports.out.caja.DibujarCartaDeCajaPort;
import com.renaser.os.chat.domain.model.bienvenida.FondoDeCarta;
import com.renaser.os.chat.domain.model.bienvenida.PortadaDeBienvenida;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * La carta con el nombre de la Caja Renaser (D-219), con Java2D y la misma Cinzel de la tarjeta de bienvenida
 * (D-174), sin servicios externos.
 *
 * <p><b>El lienzo</b> es vertical, con la proporción de una hoja A ({@value #ANCHO} × {@value #ALTO} px:
 * A5 a unos 200 ppp, A4 a 145): se imprime sin deformar en cualquier hoja de esa serie. El nombre va al
 * centro, en el verde de Renaser, completo y como está escrito; si no entra en {@value #ANCHO_MAXIMO} px, se
 * achica la letra.
 *
 * <p><b>El fondo original</b> no es un diseño de Operaciones (todavía no hay uno): es papel crema con un doble
 * marco verde, dibujado acá. Cuando Operaciones tenga el suyo, el Admin lo sube desde la app (pieza
 * {@code CARTA_CAJA}) y se escala para cubrir la hoja, recortando lo que sobre del centro.
 *
 * <p>Sale en PNG y no en JPEG: es para imprimir, y el texto con JPEG se ve con halo.
 */
@Component
class CartaDeCajaJava2dAdapter implements DibujarCartaDeCajaPort {

    static final int ANCHO = 1200;
    static final int ALTO = 1697;
    static final float ANCHO_MAXIMO = 1000f;
    private static final float TAMANO = 110f;
    private static final float LINEA_BASE_Y = ALTO / 2f + 38f;
    private static final Color PAPEL = new Color(0xFA, 0xF6, 0xEC);
    private static final Color VERDE = BienvenidaJava2dAdapter.VERDE_RENASER;
    /** Una franja de la altura de la letra alrededor del nombre: ahí se mide si se lee. */
    private static final int FRANJA_Y0 = Math.round(LINEA_BASE_Y - 100);
    private static final int FRANJA_Y1 = Math.round(LINEA_BASE_Y + 10);
    private static final double LUMINANCIA_MINIMA = 3.0 * (luminancia(VERDE.getRGB()) + 0.05) - 0.05;
    private static final Logger log = LoggerFactory.getLogger(CartaDeCajaJava2dAdapter.class);

    private final AlmacenamientoPort almacenamiento;
    private final Font cinzel;
    private final Cache<String, BufferedImage> abiertos = Caffeine.newBuilder().maximumSize(2).build();

    CartaDeCajaJava2dAdapter(AlmacenamientoPort almacenamiento) {
        this.almacenamiento = almacenamiento;
        this.cinzel = leerFuente();
    }

    @Override
    public byte[] dibujar(String nombre, String fondo) {
        BufferedImage lienzo = new BufferedImage(ANCHO, ALTO, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = lienzo.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            BufferedImage subido = fondo == null ? null : abiertoONada(fondo);
            if (subido == null) {
                pintarOriginal(g);
            } else {
                g.drawImage(subido, 0, 0, null);
            }
            if (nombre != null && !nombre.isBlank()) {
                escribirCentrado(g, nombre.strip());
            }
        } finally {
            g.dispose();
        }
        return comoPng(lienzo);
    }

    @Override
    public void revisar(String ruta) {
        BufferedImage lienzo = abrir(FondoDeCarta.exigirRutaPropia(ruta));
        PortadaDeBienvenida.exigirNombreLegible(parteOscuraDondeVaElNombre(lienzo));
        abiertos.put(ruta, lienzo);
    }

    private BufferedImage abiertoONada(String ruta) {
        try {
            return abiertos.get(ruta, this::abrir);
        } catch (RuntimeException sinFondo) {
            log.warn("[chat.caja] el fondo de la carta {} no se pudo abrir ({}): se dibuja sobre el original", ruta,
                    sinFondo.getMessage());
            return null;
        }
    }

    private BufferedImage abrir(String ruta) {
        byte[] contenido = almacenamiento.leer(ruta, PortadaDeBienvenida.PESO_MAXIMO_EN_BYTES)
                .orElseThrow(() -> new NoSuchElementException("No encontramos la imagen subida: vuelve a elegirla."));
        return cubrir(leerRevisada(contenido));
    }

    private static void pintarOriginal(Graphics2D g) {
        g.setColor(PAPEL);
        g.fillRect(0, 0, ANCHO, ALTO);
        g.setColor(VERDE);
        g.setStroke(new BasicStroke(6f));
        g.drawRect(48, 48, ANCHO - 96, ALTO - 96);
        g.setStroke(new BasicStroke(2f));
        g.drawRect(66, 66, ANCHO - 132, ALTO - 132);
    }

    private void escribirCentrado(Graphics2D g, String nombre) {
        g.setColor(VERDE);
        Font fuente = cinzel.deriveFont(TAMANO);
        float ancho = (float) fuente.getStringBounds(nombre, g.getFontRenderContext()).getWidth();
        if (ancho > ANCHO_MAXIMO) {
            fuente = cinzel.deriveFont(TAMANO * ANCHO_MAXIMO / ancho);
            ancho = (float) fuente.getStringBounds(nombre, g.getFontRenderContext()).getWidth();
        }
        g.setFont(fuente);
        g.drawString(nombre, ANCHO / 2f - ancho / 2f, LINEA_BASE_Y);
    }

    /** Abre la imagen con las reglas de la portada: JPEG o PNG por su contenido, peso y medidas. */
    private static BufferedImage leerRevisada(byte[] contenido) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
            Iterator<ImageReader> lectores = entrada == null ? null : ImageIO.getImageReaders(entrada);
            if (lectores == null || !lectores.hasNext()) {
                throw new IllegalArgumentException(PortadaDeBienvenida.NO_ES_IMAGEN);
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(entrada, true, true);
                PortadaDeBienvenida.exigirQueSePuedaUsar(lector.getFormatName(), contenido.length, lector.getWidth(0),
                        lector.getHeight(0));
                return lector.read(0);
            } finally {
                lector.dispose();
            }
        } catch (IOException danada) {
            throw new IllegalArgumentException("No se pudo abrir la imagen: puede estar dañada. Prueba con otra.",
                    danada);
        }
    }

    /** Escala para cubrir la hoja entera y recorta lo que sobra, centrado. */
    private static BufferedImage cubrir(BufferedImage imagen) {
        double escala = Math.max((double) ANCHO / imagen.getWidth(), (double) ALTO / imagen.getHeight());
        int ancho = (int) Math.ceil(imagen.getWidth() * escala);
        int alto = (int) Math.ceil(imagen.getHeight() * escala);
        BufferedImage lienzo = new BufferedImage(ANCHO, ALTO, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = lienzo.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, ANCHO, ALTO);
            g.drawImage(imagen, (ANCHO - ancho) / 2, (ALTO - alto) / 2, ancho, alto, null);
        } finally {
            g.dispose();
        }
        return lienzo;
    }

    /** De 0 a 1: cuánto de la franja del nombre es demasiado oscuro para el verde de la letra (contraste 3:1). */
    static double parteOscuraDondeVaElNombre(BufferedImage lienzo) {
        int oscuros = 0;
        int mirados = 0;
        int x0 = Math.round(ANCHO / 2f - ANCHO_MAXIMO / 2);
        int x1 = Math.round(ANCHO / 2f + ANCHO_MAXIMO / 2);
        for (int y = FRANJA_Y0; y < FRANJA_Y1; y += 2) {
            for (int x = x0; x < x1; x += 2) {
                mirados++;
                if (luminancia(lienzo.getRGB(x, y)) < LUMINANCIA_MINIMA) {
                    oscuros++;
                }
            }
        }
        return mirados == 0 ? 0 : (double) oscuros / mirados;
    }

    private static double luminancia(int rgb) {
        return 0.2126 * lineal((rgb >> 16) & 0xFF) + 0.7152 * lineal((rgb >> 8) & 0xFF) + 0.0722 * lineal(rgb & 0xFF);
    }

    private static double lineal(int canal) {
        double c = canal / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static byte[] comoPng(BufferedImage imagen) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            ImageIO.write(imagen, "png", salida);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo codificar la carta", e);
        }
        return salida.toByteArray();
    }

    private static Font leerFuente() {
        try (InputStream entrada = CartaDeCajaJava2dAdapter.class.getResourceAsStream("/bienvenida/Cinzel.ttf")) {
            if (entrada == null) {
                throw new IllegalStateException("Falta el recurso /bienvenida/Cinzel.ttf en el jar");
            }
            return Font.createFont(Font.TRUETYPE_FONT, entrada);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la fuente de la carta", e);
        } catch (FontFormatException e) {
            throw new IllegalStateException("La fuente de la carta no es TrueType válida", e);
        }
    }
}
