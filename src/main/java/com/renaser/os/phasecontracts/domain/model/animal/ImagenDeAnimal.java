package com.renaser.os.phasecontracts.domain.model.animal;

import java.util.Locale;
import java.util.Set;

/**
 * Qué imagen sirve para el animal de una fase (D-258). Los números son decisiones técnicas a confirmar
 * con el dueño: PNG o WebP (lo recomendado es con fondo transparente), hasta 2 MB, entre 256 y 4096 px.
 * El formato se mira por el contenido ({@link CabeceraDeImagen}), no por el tipo que declaró quien subió.
 */
public final class ImagenDeAnimal {

    public static final String PREFIJO_RUTA = "fases/animales/";
    public static final Set<String> TIPOS_DE_CONTENIDO = Set.of("image/png", "image/webp");
    public static final long PESO_MAXIMO_EN_BYTES = 2L * 1024 * 1024;
    public static final int LADO_MINIMO = 256;
    public static final int LADO_MAXIMO = 4096;
    public static final String NO_ES_IMAGEN = "Esa imagen no es PNG ni WebP.";

    private static final int LARGO_MAXIMO_DE_RUTA = 300;

    private ImagenDeAnimal() {
    }

    public static void exigirTipoDeContenido(String tipoContenido) {
        if (tipoContenido == null || !TIPOS_DE_CONTENIDO.contains(tipoContenido.strip().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("La imagen tiene que ser PNG o WebP.");
        }
    }

    /** Solo rutas que emitió el servidor para un animal: nunca la de otro objeto del depósito. */
    public static String exigirRutaPropia(String ruta) {
        boolean propia = ruta != null && ruta.startsWith(PREFIJO_RUTA) && ruta.length() > PREFIJO_RUTA.length()
                && ruta.length() <= LARGO_MAXIMO_DE_RUTA && !ruta.contains("..") && !ruta.contains("//");
        if (!propia) {
            throw new IllegalArgumentException("La imagen tiene que ser una subida para el animal de una fase.");
        }
        return ruta;
    }

    public static void exigirQueSePuedaUsar(CabeceraDeImagen cabecera, long pesoEnBytes) {
        if (pesoEnBytes > PESO_MAXIMO_EN_BYTES) {
            throw new IllegalArgumentException("La imagen pesa " + megas(pesoEnBytes) + " MB: el máximo es "
                    + megas(PESO_MAXIMO_EN_BYTES) + " MB.");
        }
        if (Math.min(cabecera.ancho(), cabecera.alto()) < LADO_MINIMO) {
            throw new IllegalArgumentException("La imagen es muy chica (" + cabecera.ancho() + " × "
                    + cabecera.alto() + " px): tiene que medir al menos " + LADO_MINIMO + " px por lado.");
        }
        if (Math.max(cabecera.ancho(), cabecera.alto()) > LADO_MAXIMO) {
            throw new IllegalArgumentException("La imagen es demasiado grande (" + cabecera.ancho() + " × "
                    + cabecera.alto() + " px): el máximo es " + LADO_MAXIMO + " px por lado.");
        }
    }

    private static String megas(long bytes) {
        return String.format(Locale.forLanguageTag("es"), "%.1f", bytes / (1024.0 * 1024.0)).replace(",0", "");
    }
}
