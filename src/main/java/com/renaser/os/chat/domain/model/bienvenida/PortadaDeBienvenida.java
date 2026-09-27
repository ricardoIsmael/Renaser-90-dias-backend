package com.renaser.os.chat.domain.model.bienvenida;

import java.util.Locale;
import java.util.Set;

/**
 * Las reglas de una portada nueva para la tarjeta de bienvenida (D-210): qué imagen sirve.
 *
 * <p>Cómo se abre la imagen y cuánto de ella queda oscuro lo mide la infraestructura (Java2D); qué es
 * aceptable lo decide esta clase, con mensajes que la app muestra tal cual. Los números son decisiones
 * técnicas, a confirmar con el dueño:
 * <ul>
 *   <li><b>JPEG o PNG</b>: lo que exporta Canva y lo que sale del teléfono (la app recorta y manda JPEG).
 *       Se mira el contenido, no el tipo que declaró quien subió.</li>
 *   <li><b>Hasta {@link #PESO_MAXIMO_EN_BYTES}</b> (5 MB): la exportación de Canva de hoy pesa 1,2 MB y la
 *       app manda ~0,3–0,6 MB. Más que eso no aporta nada a una tarjeta de 1200 px.</li>
 *   <li><b>Entre {@link #LADO_MINIMO} y {@link #LADO_MAXIMO} px por lado.</b> La tarjeta mide 1200 × 1200:
 *       por debajo de 600 se agranda al doble y se ve borrosa; el tope protege al servidor de abrir una
 *       imagen gigante. Si no es cuadrada, se usa el centro.</li>
 *   <li><b>El nombre se tiene que leer.</b> El nombre va en verde oscuro, abajo al centro. Si más de
 *       {@link #PARTE_OSCURA_MAXIMA} de esa franja es tan oscura que el verde no llega a un contraste de
 *       3:1 (el mínimo de WCAG para letra grande), se rechaza.</li>
 * </ul>
 */
public final class PortadaDeBienvenida {

    /** Dónde se suben las portadas. Solo se aceptan rutas bajo este prefijo: las que emite el servidor. */
    public static final String PREFIJO_RUTA = "bienvenida/portadas/";
    public static final Set<String> TIPOS_DE_CONTENIDO = Set.of("image/jpeg", "image/png");
    public static final long PESO_MAXIMO_EN_BYTES = 5L * 1024 * 1024;
    public static final int LADO_MINIMO = 600;
    public static final int LADO_MAXIMO = 8000;
    public static final double PARTE_OSCURA_MAXIMA = 0.10;
    public static final String NO_ES_IMAGEN = "Esa imagen no es JPG ni PNG.";

    private static final Set<String> FORMATOS = Set.of("jpeg", "png");
    private static final int LARGO_MAXIMO_DE_RUTA = 300;

    private PortadaDeBienvenida() {
    }

    /** @throws IllegalArgumentException si no es JPEG ni PNG */
    public static void exigirTipoDeContenido(String tipoContenido) {
        if (tipoContenido == null || !TIPOS_DE_CONTENIDO.contains(tipoContenido.strip().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("La portada tiene que ser una imagen JPG o PNG.");
        }
    }

    /**
     * La ruta tiene que ser una que emitió el servidor para una portada: nunca la de otro objeto del
     * depósito (evidencias, firmas, avatares), que después se abriría y se dibujaría como tarjeta.
     *
     * @return la ruta tal cual
     * @throws IllegalArgumentException si apunta fuera de las portadas
     */
    public static String exigirRutaPropia(String ruta) {
        boolean propia = ruta != null && ruta.startsWith(PREFIJO_RUTA) && ruta.length() > PREFIJO_RUTA.length()
                && ruta.length() <= LARGO_MAXIMO_DE_RUTA && !ruta.contains("..") && !ruta.contains("//");
        if (!propia) {
            throw new IllegalArgumentException("La portada tiene que ser una imagen subida para la tarjeta ("
                    + PREFIJO_RUTA + "…)");
        }
        return ruta;
    }

    /** Lo que se sabe sin abrir la imagen: el formato (por su contenido), el peso y las medidas. */
    public static void exigirQueSePuedaUsar(String formato, long pesoEnBytes, int ancho, int alto) {
        if (formato == null || !FORMATOS.contains(formato.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(NO_ES_IMAGEN);
        }
        if (pesoEnBytes > PESO_MAXIMO_EN_BYTES) {
            throw new IllegalArgumentException("La imagen pesa " + megas(pesoEnBytes) + " MB: el máximo es "
                    + megas(PESO_MAXIMO_EN_BYTES) + " MB.");
        }
        if (Math.min(ancho, alto) < LADO_MINIMO) {
            throw new IllegalArgumentException("La imagen es muy chica (" + ancho + " × " + alto
                    + " px): tiene que medir al menos " + LADO_MINIMO + " px por lado.");
        }
        if (Math.max(ancho, alto) > LADO_MAXIMO) {
            throw new IllegalArgumentException("La imagen es demasiado grande (" + ancho + " × " + alto
                    + " px): el máximo es " + LADO_MAXIMO + " px por lado.");
        }
    }

    /** @param parteOscura de 0 a 1: cuánto de la franja del nombre no le da contraste al verde de la letra */
    public static void exigirNombreLegible(double parteOscura) {
        if (parteOscura > PARTE_OSCURA_MAXIMA) {
            throw new IllegalArgumentException("El nombre no se leería: la franja donde va (abajo, al centro) es "
                    + "muy oscura. Elige una imagen más clara en esa parte.");
        }
    }

    private static String megas(long bytes) {
        return String.format(Locale.forLanguageTag("es"), "%.1f", bytes / (1024.0 * 1024.0)).replace(",0", "");
    }
}
