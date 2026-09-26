package com.renaser.os.chat.infrastructure.adapter.out.bienvenida;

import com.renaser.os.chat.application.ports.out.bienvenida.DibujarBienvenidaPort;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;

/**
 * Escribe el nombre sobre el fondo del Canva con Java2D, sin servicios externos (D-174).
 *
 * <p><b>De dónde salen los números.</b> Se midieron comparando las dos exportaciones de Canva que
 * pasó Operaciones (el fondo sin nombre y el mismo con "FLOR DE MARÍA"): Cinzel Regular, altura de
 * mayúscula 96 px (tamaño 133 en un lienzo de 1200), letras 7,5 px más juntas que lo normal,
 * centrado en x = 600 con la línea base en y = 868, color #153832. Con esos valores la diferencia
 * contra el original es solo el suavizado de bordes; lo fija {@code BienvenidaJava2dAdapterTest}.
 *
 * <p><b>Nombres largos.</b> Si el nombre no entra en {@link #ANCHO_MAXIMO}, se achica la letra
 * manteniendo la línea base, en vez de salirse del cuadro.
 *
 * <p>Sale en JPEG y no en PNG: el fondo es una foto con degradés, y en PNG pesa 1,2 MB contra
 * ~200 KB, que es lo que tarda en cargar con datos móviles.
 */
@Component
class BienvenidaJava2dAdapter implements DibujarBienvenidaPort {

    private static final float TAMANO = 133f;
    private static final float SEPARACION_EN_EM = -7.5f / 133f;
    private static final float CENTRO_X = 600f;
    private static final float LINEA_BASE_Y = 868f;
    private static final float ANCHO_MAXIMO = 1040f;
    private static final Color VERDE_RENASER = new Color(0x15, 0x38, 0x32);
    private static final float CALIDAD_JPEG = 0.88f;
    private static final Locale ESPANOL = Locale.forLanguageTag("es");

    private final BufferedImage fondo;
    private final Font cinzel;

    BienvenidaJava2dAdapter() {
        this.fondo = leerFondo("/bienvenida/fondo.png");
        this.cinzel = leerFuente("/bienvenida/Cinzel.ttf");
    }

    @Override
    public byte[] dibujar(String nombre) {
        BufferedImage lienzo = new BufferedImage(fondo.getWidth(), fondo.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = lienzo.createGraphics();
        try {
            g.drawImage(fondo, 0, 0, null);
            if (nombre != null && !nombre.isBlank()) {
                escribirCentrado(g, nombre.strip().toUpperCase(ESPANOL));
            }
        } finally {
            g.dispose();
        }
        return comoJpeg(lienzo);
    }

    /** Letra por letra, como Canva: así se aplica la separación entre letras de su diseño. */
    private void escribirCentrado(Graphics2D g, String texto) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(VERDE_RENASER);
        FontRenderContext contexto = g.getFontRenderContext();
        float anchoNatural = ancho(texto, cinzel.deriveFont(TAMANO), contexto);
        float tamano = anchoNatural <= ANCHO_MAXIMO ? TAMANO : TAMANO * ANCHO_MAXIMO / anchoNatural;
        Font fuente = cinzel.deriveFont(tamano);
        g.setFont(fuente);
        float x = CENTRO_X - ancho(texto, fuente, contexto) / 2f;
        float separacion = SEPARACION_EN_EM * tamano;
        for (String letra : letras(texto)) {
            g.drawString(letra, x, LINEA_BASE_Y);
            x += avance(letra, fuente, contexto) + separacion;
        }
    }

    private static float ancho(String texto, Font fuente, FontRenderContext contexto) {
        String[] letras = letras(texto);
        float total = SEPARACION_EN_EM * fuente.getSize2D() * (letras.length - 1);
        for (String letra : letras) {
            total += avance(letra, fuente, contexto);
        }
        return total;
    }

    private static float avance(String letra, Font fuente, FontRenderContext contexto) {
        return (float) fuente.getStringBounds(letra, contexto).getWidth();
    }

    private static String[] letras(String texto) {
        return texto.codePoints().mapToObj(Character::toString).toArray(String[]::new);
    }

    private static byte[] comoJpeg(BufferedImage imagen) {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam parametros = escritor.getDefaultWriteParam();
        parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        parametros.setCompressionQuality(CALIDAD_JPEG);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ImageOutputStream flujo = ImageIO.createImageOutputStream(salida)) {
            escritor.setOutput(flujo);
            escritor.write(null, new IIOImage(imagen, null, null), parametros);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo codificar la tarjeta de bienvenida", e);
        } finally {
            escritor.dispose();
        }
        return salida.toByteArray();
    }

    private static BufferedImage leerFondo(String recurso) {
        try (InputStream entrada = abrir(recurso)) {
            return ImageIO.read(entrada);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el fondo de bienvenida " + recurso, e);
        }
    }

    private static Font leerFuente(String recurso) {
        try (InputStream entrada = abrir(recurso)) {
            return Font.createFont(Font.TRUETYPE_FONT, entrada);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la fuente " + recurso, e);
        } catch (FontFormatException e) {
            throw new IllegalStateException("La fuente " + recurso + " no es TrueType válida", e);
        }
    }

    private static InputStream abrir(String recurso) {
        InputStream entrada = BienvenidaJava2dAdapter.class.getResourceAsStream(recurso);
        if (entrada == null) {
            throw new IllegalStateException("Falta el recurso " + recurso + " en el jar");
        }
        return entrada;
    }
}
