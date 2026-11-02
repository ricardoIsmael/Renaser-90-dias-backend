package com.renaser.os.chat.domain.model.bienvenida;

/**
 * Las reglas de la imagen de una formación en el chat (spec 035). Qué imagen sirve (JPEG o PNG, peso y medidas) es lo mismo que para la portada de la bienvenida
 * ({@link PortadaDeBienvenida}); lo único propio es dónde se sube y que NO se mide si un nombre se lee encima: es un avatar, no una tarjeta con nombre.
 */
public final class ImagenDeLaFormacion {

    /** Dónde se suben las imágenes de las formaciones. Solo se aceptan rutas bajo este prefijo (V159 lo exige en la base). */
    public static final String PREFIJO_RUTA = "formaciones/imagenes/";
    /** El lado de la imagen que se sirve: el avatar más grande del chat son 120 px lógicos (360 físicos). */
    public static final int LADO_SERVIDO = 512;
    /** Lo que vale «la formación no tiene ninguna imagen subida» en {@code EstadoDePieza}: los chats usan lo que trae la app. */
    public static final String SIN_IMAGEN = "predeterminada";
    private static final int LARGO_MAXIMO_DE_RUTA = 300;

    private ImagenDeLaFormacion() {
    }

    /** @throws IllegalArgumentException si no es JPEG ni PNG */
    public static void exigirTipoDeContenido(String tipoContenido) {
        try {
            PortadaDeBienvenida.exigirTipoDeContenido(tipoContenido);
        } catch (IllegalArgumentException noEsImagen) {
            throw new IllegalArgumentException("La imagen tiene que ser JPG o PNG.");
        }
    }

    /**
     * @return la ruta tal cual
     * @throws IllegalArgumentException si no es una ruta que emitió el servidor para la imagen de una formación (nunca la de otro objeto del depósito)
     */
    public static String exigirRutaPropia(String ruta) {
        boolean propia = ruta != null && ruta.startsWith(PREFIJO_RUTA) && ruta.length() > PREFIJO_RUTA.length()
                && ruta.length() <= LARGO_MAXIMO_DE_RUTA && !ruta.contains("..") && !ruta.contains("//");
        if (!propia) {
            throw new IllegalArgumentException("La imagen tiene que ser una subida para la formación (" + PREFIJO_RUTA + "…)");
        }
        return ruta;
    }
}
