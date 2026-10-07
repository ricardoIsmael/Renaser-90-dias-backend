package com.renaser.os.chat.infrastructure.adapter.out.ranking;

import com.renaser.os.chat.domain.model.ranking.PodioDeLaSemana.Puesto;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.math.RoundingMode;
import java.util.List;

/**
 * Las tres columnas del podio (D-262): medalla, nombre, puntaje y bloque, de abajo hacia arriba como en la maqueta.
 * El primero de la lista va al centro, el segundo a la izquierda y el tercero a la derecha; el color y la altura
 * salen del puesto (oro, plata, bronce), así un empate muestra dos bloques iguales.
 */
final class ColumnasDelPodio {

    private static final float IZQUIERDA = 90f;
    private static final float SEPARACION = 26f;
    private static final float ANCHO_COLUMNA = (900f - 2 * SEPARACION) / 3f;
    private static final float PISO = 1060f;
    /** Columna de cada uno de los tres primeros de la lista: centro, izquierda, derecha. */
    private static final int[] COLUMNA_DE = {1, 0, 2};

    private record Estilo(int alturaBloque, Color[] bloque, float[] cortesBloque, int medalla, float numero,
                          Color[] colorMedalla) {
    }

    private static final Estilo ORO = new Estilo(250, colores(0xC4A665, 0xB2924F, 0x9A7D40), new float[]{0f, 0.4f, 1f},
            116, 56f, colores(0xD6BB7C, 0xB2924F, 0x8E7238));
    private static final Estilo PLATA = new Estilo(180, colores(0xD3CFC6, 0xBDB8AD), new float[]{0f, 1f},
            96, 46f, colores(0xD9D9D6, 0xA9A9A4, 0x86867F));
    private static final Estilo BRONCE = new Estilo(130, colores(0xD9B597, 0xC49A76), new float[]{0f, 1f},
            96, 46f, colores(0xDDB089, 0xB4794A, 0x8C5A33));

    private final TiposDelPodio tipos;

    ColumnasDelPodio(TiposDelPodio tipos) {
        this.tipos = tipos;
    }

    void dibujar(Graphics2D g, List<Puesto> podio) {
        for (int i = 0; i < podio.size(); i++) {
            columna(g, podio.get(i), IZQUIERDA + COLUMNA_DE[i] * (ANCHO_COLUMNA + SEPARACION));
        }
    }

    /** «96,4»: siempre un decimal, con coma. */
    static String puntaje(Puesto puesto) {
        return puesto.puntaje().setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private void columna(Graphics2D g, Puesto puesto, float x) {
        Estilo estilo = estiloDe(puesto.lugar());
        float techo = PISO - estilo.alturaBloque();
        float centro = x + ANCHO_COLUMNA / 2f;
        bloque(g, estilo, x, techo);
        puntos(g, puesto, centro, techo - 33f);
        nombre(g, puesto.nombre(), centro, techo - 76f);
        medalla(g, estilo, puesto.lugar(), centro, techo - 76f - 58f - estilo.medalla() / 2f);
    }

    private static Estilo estiloDe(int lugar) {
        return switch (lugar) {
            case 1 -> ORO;
            case 2 -> PLATA;
            default -> BRONCE;
        };
    }

    /** Esquinas redondeadas solo arriba: el rectángulo sigue debajo del piso y se recorta ahí. */
    private static void bloque(Graphics2D g, Estilo estilo, float x, float techo) {
        Shape recorteAnterior = g.getClip();
        g.clip(new Rectangle2D.Float(x, techo, ANCHO_COLUMNA, PISO - techo));
        g.setPaint(new LinearGradientPaint(x, techo, x, PISO, estilo.cortesBloque(), estilo.bloque()));
        g.fill(new RoundRectangle2D.Float(x, techo, ANCHO_COLUMNA, PISO - techo + 44f, 44f, 44f));
        g.setClip(recorteAnterior);
    }

    private void puntos(Graphics2D g, Puesto puesto, float centro, float lineaBase) {
        Font negrita = tipos.jostSemibold.deriveFont(28f);
        Font regular = tipos.jostRegular.deriveFont(28f);
        String numero = puntaje(puesto);
        float x = centro - (TiposDelPodio.ancho(g, numero, negrita, 0f) + TiposDelPodio.ancho(g, " pts", regular, 0f)) / 2f;
        g.setColor(PodioJava2dAdapter.TINTA);
        x = TiposDelPodio.escribir(g, numero, negrita, x, lineaBase, 0f);
        g.setColor(PodioJava2dAdapter.SUAVE);
        TiposDelPodio.escribir(g, " pts", regular, x, lineaBase, 0f);
    }

    /** Un nombre que no entra en la columna achica la letra, manteniendo la línea base. */
    private void nombre(Graphics2D g, String nombre, float centro, float lineaBase) {
        Font fuente = tipos.frauncesSemibold.deriveFont(44f);
        float ancho = TiposDelPodio.ancho(g, nombre, fuente, 0f);
        float maximo = ANCHO_COLUMNA - 12f;
        if (ancho > maximo) {
            fuente = fuente.deriveFont(44f * maximo / ancho);
            ancho = TiposDelPodio.ancho(g, nombre, fuente, 0f);
        }
        g.setColor(PodioJava2dAdapter.TEXTO);
        TiposDelPodio.escribir(g, nombre, fuente, centro - ancho / 2f, lineaBase, 0f);
    }

    private void medalla(Graphics2D g, Estilo estilo, int lugar, float cx, float cy) {
        float radio = estilo.medalla() / 2f;
        sombra(g, cx, cy + 8f, radio);
        double angulo = Math.toRadians(160);
        float dx = (float) Math.sin(angulo) * radio;
        float dy = (float) -Math.cos(angulo) * radio;
        g.setPaint(new LinearGradientPaint(cx - dx, cy - dy, cx + dx, cy + dy, new float[]{0f, 0.55f, 1f},
                estilo.colorMedalla()));
        g.fill(new Ellipse2D.Float(cx - radio, cy - radio, 2 * radio, 2 * radio));
        Font fuente = tipos.frauncesSemibold.deriveFont(estilo.numero());
        GlyphVector digito = fuente.createGlyphVector(g.getFontRenderContext(), String.valueOf(lugar));
        Rectangle2D caja = digito.getVisualBounds();
        g.setColor(Color.WHITE);
        g.drawGlyphVector(digito, (float) (cx - caja.getCenterX()), (float) (cy - caja.getCenterY()));
    }

    /** {@code box-shadow: 0 8px 20px rgba(60,45,15,.18)}, aproximada con anillos cada vez más tenues. */
    private static void sombra(Graphics2D g, float cx, float cy, float radio) {
        for (int paso = 20; paso >= 0; paso -= 2) {
            int alfa = Math.round(46f * (1f - paso / 20f) / 6f);
            g.setColor(new Color(60, 45, 15, alfa));
            float r = radio + paso / 2f;
            g.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
        }
    }

    private static Color[] colores(int... rgb) {
        Color[] colores = new Color[rgb.length];
        for (int i = 0; i < rgb.length; i++) {
            colores[i] = new Color(rgb[i]);
        }
        return colores;
    }
}
