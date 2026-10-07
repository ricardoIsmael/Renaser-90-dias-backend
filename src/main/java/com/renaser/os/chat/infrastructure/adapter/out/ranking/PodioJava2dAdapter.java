package com.renaser.os.chat.infrastructure.adapter.out.ranking;

import com.renaser.os.chat.application.ports.out.ranking.DibujarPodioPort;
import com.renaser.os.chat.domain.model.ranking.PodioDeLaSemana;
import com.renaser.os.chat.domain.model.ranking.PodioDeLaSemana.Puesto;
import com.renaser.os.chat.domain.model.ranking.SemanaDelRanking;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * La imagen del podio de la semana con Java2D, sin servicios externos (D-262), fiel a la maqueta que eligió el dueño
 * («propuesta A, podio»): 1080 × 1350, fondo crema, el fénix, oro al centro, plata a la izquierda, bronce a la
 * derecha y el 4.º y el 5.º en dos cápsulas debajo. Sin emojis dentro de la imagen.
 *
 * <p><b>De dónde salen los números.</b> Del CSS de la maqueta y de medir su PNG: las líneas base de cada texto,
 * la altura de cada bloque (oro 250, plata 180, bronce 130) y su separación (26) son las de la maqueta.
 *
 * <p><b>Empates</b>: el color y la altura del bloque son los del PUESTO, no los de la columna. Dos primeros
 * empatados son dos bloques de oro; el centro es siempre el primero de la lista. Las columnas que faltan (menos de
 * tres personas) no se dibujan.
 *
 * <p>Sale en JPEG de calidad 0,90 (E-590): pesa la mitad que el PNG (≈110 KB contra ≈216 KB) y en el chat aparece
 * antes con datos móviles; a calidad 0,90 las letras no se ven sucias ni con zoom ×3.
 *
 * <blockquote><b>Corregido 2026-10-07.</b> Decía <i>«Sale en PNG: son colores planos y letras, que en JPEG se ensucian
 * alrededor de los bordes»</i>. El fondo y los bloques llevan degradados, el PNG pesaba 216 KB y la imagen tardaba en
 * aparecer en el chat; la comparación con zoom ×3 no mostró bordes sucios a calidad 0,90.</blockquote>
 */
@Component
class PodioJava2dAdapter implements DibujarPodioPort {

    static final int ANCHO = 1080;
    static final int ALTO = 1350;
    static final float CALIDAD_JPEG = 0.90f;
    static final Color FONDO = new Color(0xFC, 0xFB, 0xF9);
    static final Color DORADO = new Color(0xB2, 0x92, 0x4F);
    static final Color TINTA = new Color(0x85, 0x6C, 0x35);
    static final Color TEXTO = new Color(0x1E, 0x1B, 0x18);
    static final Color SUAVE = new Color(0x4A, 0x45, 0x3D);
    static final Color LINEA = new Color(0xE2, 0xDC, 0xD2);
    static final Color VERDE = new Color(0x15, 0x38, 0x32);

    private final TiposDelPodio tipos = new TiposDelPodio();
    private final ColumnasDelPodio columnas = new ColumnasDelPodio(tipos);

    @Override
    public byte[] dibujar(PodioDeLaSemana podio, SemanaDelRanking semana) {
        BufferedImage lienzo = new BufferedImage(ANCHO, ALTO, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = lienzo.createGraphics();
        try {
            suavizar(g);
            fondo(g);
            encabezado(g, semana);
            g.drawImage(tipos.fenix, 440, 300, 200, 200, null);
            columnas.dibujar(g, podio.podio());
            g.setColor(LINEA);
            g.fill(new RoundRectangle2D.Float(60, 1060, ANCHO - 120, 4, 4, 4));
            capsulas(g, podio.debajo());
            pie(g);
        } finally {
            g.dispose();
        }
        return comoJpeg(lienzo);
    }

    private static void suavizar(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    /** El crema y el halo dorado detrás del fénix y del podio ({@code radial-gradient(closest-side …)}). */
    private static void fondo(Graphics2D g) {
        g.setColor(FONDO);
        g.fillRect(0, 0, ANCHO, ALTO);
        Point2D centro = new Point2D.Float(540, 600);
        AffineTransform elipse = new AffineTransform();
        elipse.translate(540, 600);
        elipse.scale(1, 0.75);
        elipse.translate(-540, -600);
        g.setPaint(new RadialGradientPaint(centro, 600, centro, new float[]{0f, 1f},
                new Color[]{new Color(178, 146, 79, 46), new Color(178, 146, 79, 0)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE, MultipleGradientPaint.ColorSpaceType.SRGB, elipse));
        g.fill(new Rectangle2D.Float(0, 150, ANCHO, 900));
    }

    private void encabezado(Graphics2D g, SemanaDelRanking semana) {
        g.setColor(TINTA);
        centrado(g, "FORMACIÓN RENASER · RANKING GENERAL", tipos.jostSemibold.deriveFont(24f), 95, 24f * 0.32f);
        Font recta = tipos.frauncesSemibold.deriveFont(92f);
        Font italica = tipos.frauncesItalica.deriveFont(92f);
        String antes = "Podio de la ";
        float x = (ANCHO - TiposDelPodio.ancho(g, antes, recta, 0f) - TiposDelPodio.ancho(g, "semana", italica, 0f)) / 2f;
        g.setColor(TEXTO);
        x = TiposDelPodio.escribir(g, antes, recta, x, 206, 0f);
        g.setColor(DORADO);
        TiposDelPodio.escribir(g, "semana", italica, x, 206, 0f);
        g.setColor(SUAVE);
        centrado(g, semana.rango(), tipos.jostRegular.deriveFont(30f), 270, 0f);
    }

    /** El 4.º y el 5.º: «4.º Rosa T. · 85,0» en cápsulas blancas centradas, con 24 px entre ellas. */
    private void capsulas(Graphics2D g, List<Puesto> debajo) {
        if (debajo.isEmpty()) {
            return;
        }
        Font regular = tipos.jostRegular.deriveFont(28f);
        Font negrita = tipos.jostSemibold.deriveFont(28f);
        float[] anchos = new float[debajo.size()];
        float total = 24f * (debajo.size() - 1);
        for (int i = 0; i < debajo.size(); i++) {
            anchos[i] = anchoDeCapsula(g, debajo.get(i), regular, negrita);
            total += anchos[i];
        }
        float x = (ANCHO - total) / 2f;
        for (int i = 0; i < debajo.size(); i++) {
            capsula(g, debajo.get(i), x, anchos[i], regular, negrita);
            x += anchos[i] + 24f;
        }
    }

    private static float anchoDeCapsula(Graphics2D g, Puesto puesto, Font regular, Font negrita) {
        return 60f + TiposDelPodio.ancho(g, ordinal(puesto), regular, 0f)
                + TiposDelPodio.ancho(g, puesto.nombre(), negrita, 0f)
                + TiposDelPodio.ancho(g, " · " + ColumnasDelPodio.puntaje(puesto), regular, 0f);
    }

    private static void capsula(Graphics2D g, Puesto puesto, float x, float ancho, Font regular, Font negrita) {
        RoundRectangle2D forma = new RoundRectangle2D.Float(x, 1100, ancho, 70, 70, 70);
        g.setColor(Color.WHITE);
        g.fill(forma);
        g.setColor(LINEA);
        g.setStroke(new java.awt.BasicStroke(1.5f));
        g.draw(forma);
        g.setColor(SUAVE);
        float cursor = TiposDelPodio.escribir(g, ordinal(puesto), regular, x + 30, 1145, 0f);
        g.setColor(TEXTO);
        cursor = TiposDelPodio.escribir(g, puesto.nombre(), negrita, cursor, 1145, 0f);
        g.setColor(SUAVE);
        TiposDelPodio.escribir(g, " · " + ColumnasDelPodio.puntaje(puesto), regular, cursor, 1145, 0f);
    }

    private static String ordinal(Puesto puesto) {
        return puesto.lugar() + ".º ";
    }

    private void pie(Graphics2D g) {
        g.setColor(VERDE);
        centrado(g, "El próximo lugar en el podio puede ser el tuyo.", tipos.frauncesItalica.deriveFont(38f), 1234, 0f);
        g.setColor(TINTA);
        centrado(g, "RENASER · 90 DÍAS", tipos.jostSemibold.deriveFont(22f), 1283, 22f * 0.28f);
    }

    static void centrado(Graphics2D g, String texto, Font fuente, float lineaBase, float separacion) {
        float x = (ANCHO - TiposDelPodio.ancho(g, texto, fuente, separacion)) / 2f;
        TiposDelPodio.escribir(g, texto, fuente, x, lineaBase, separacion);
    }

    private static byte[] comoJpeg(BufferedImage imagen) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream destino = ImageIO.createImageOutputStream(salida)) {
            escritor.setOutput(destino);
            escritor.write(null, new IIOImage(imagen, null, null), conCalidad(escritor));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo codificar la imagen del podio", e);
        } finally {
            escritor.dispose();
        }
        return salida.toByteArray();
    }

    private static ImageWriteParam conCalidad(ImageWriter escritor) {
        ImageWriteParam parametros = escritor.getDefaultWriteParam();
        parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        parametros.setCompressionQuality(CALIDAD_JPEG);
        return parametros;
    }
}
