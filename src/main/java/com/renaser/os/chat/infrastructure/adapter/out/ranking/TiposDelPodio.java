package com.renaser.os.chat.infrastructure.adapter.out.ranking;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/**
 * Las letras y el fénix del podio (D-262), leídos una vez del jar: Fraunces y Jost (OFL, {@code ranking-semanal/OFL.txt}),
 * las mismas de la maqueta aprobada por el dueño. Escribe texto con separación entre letras, como el CSS de la
 * maqueta ({@code letter-spacing}).
 */
final class TiposDelPodio {

    final Font frauncesSemibold;
    final Font frauncesItalica;
    final Font jostRegular;
    final Font jostSemibold;
    final BufferedImage fenix;

    TiposDelPodio() {
        this.frauncesSemibold = fuente("/ranking-semanal/Fraunces-SemiBold.ttf");
        this.frauncesItalica = fuente("/ranking-semanal/Fraunces-Italic.ttf");
        this.jostRegular = fuente("/ranking-semanal/Jost-Regular.ttf");
        this.jostSemibold = fuente("/ranking-semanal/Jost-SemiBold.ttf");
        this.fenix = imagen("/ranking-semanal/fenix.png");
    }

    /** El ancho de {@code texto} con {@code separacion} px entre letra y letra (sin la del final). */
    static float ancho(Graphics2D g, String texto, Font fuente, float separacion) {
        FontRenderContext contexto = g.getFontRenderContext();
        if (separacion == 0f) {
            return (float) fuente.getStringBounds(texto, contexto).getWidth();
        }
        String[] letras = letras(texto);
        float total = separacion * (letras.length - 1);
        for (String letra : letras) {
            total += (float) fuente.getStringBounds(letra, contexto).getWidth();
        }
        return total;
    }

    /** Escribe desde {@code x} y devuelve dónde terminó. */
    static float escribir(Graphics2D g, String texto, Font fuente, float x, float lineaBase, float separacion) {
        g.setFont(fuente);
        if (separacion == 0f) {
            g.drawString(texto, x, lineaBase);
            return x + ancho(g, texto, fuente, 0f);
        }
        float cursor = x;
        for (String letra : letras(texto)) {
            g.drawString(letra, cursor, lineaBase);
            cursor += (float) fuente.getStringBounds(letra, g.getFontRenderContext()).getWidth() + separacion;
        }
        return cursor - separacion;
    }

    private static String[] letras(String texto) {
        return texto.codePoints().mapToObj(Character::toString).toArray(String[]::new);
    }

    private static Font fuente(String recurso) {
        try (InputStream entrada = abrir(recurso)) {
            return Font.createFont(Font.TRUETYPE_FONT, entrada);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la fuente " + recurso, e);
        } catch (FontFormatException e) {
            throw new IllegalStateException("La fuente " + recurso + " no es TrueType válida", e);
        }
    }

    private static BufferedImage imagen(String recurso) {
        try (InputStream entrada = abrir(recurso)) {
            return ImageIO.read(entrada);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la imagen " + recurso, e);
        }
    }

    private static InputStream abrir(String recurso) {
        InputStream entrada = TiposDelPodio.class.getResourceAsStream(recurso);
        if (entrada == null) {
            throw new IllegalStateException("Falta el recurso " + recurso + " en el jar");
        }
        return entrada;
    }
}
