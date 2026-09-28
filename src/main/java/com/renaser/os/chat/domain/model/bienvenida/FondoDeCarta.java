package com.renaser.os.chat.domain.model.bienvenida;

/**
 * Las reglas del fondo de la carta con el nombre de la Caja Renaser (D-219). Las de la imagen (JPEG o PNG,
 * peso, medidas, que el nombre se lea) son las mismas de la portada de la bienvenida
 * ({@link PortadaDeBienvenida}); lo único propio es dónde se sube.
 */
public final class FondoDeCarta {

    /** Dónde se suben los fondos de la carta. Solo se aceptan rutas bajo este prefijo (V82 lo exige en la base). */
    public static final String PREFIJO_RUTA = "caja/cartas/";
    private static final int LARGO_MAXIMO_DE_RUTA = 300;

    private FondoDeCarta() {
    }

    /**
     * @return la ruta tal cual
     * @throws IllegalArgumentException si no es una ruta que emitió el servidor para una carta (400)
     */
    public static String exigirRutaPropia(String ruta) {
        boolean propia = ruta != null && ruta.startsWith(PREFIJO_RUTA) && ruta.length() > PREFIJO_RUTA.length()
                && ruta.length() <= LARGO_MAXIMO_DE_RUTA && !ruta.contains("..") && !ruta.contains("//");
        if (!propia) {
            throw new IllegalArgumentException("El fondo tiene que ser una imagen subida para la carta ("
                    + PREFIJO_RUTA + "…)");
        }
        return ruta;
    }
}
