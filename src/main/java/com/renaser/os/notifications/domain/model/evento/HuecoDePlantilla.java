package com.renaser.os.notifications.domain.model.evento;

/**
 * Un hueco de la plantilla de WhatsApp ya limpio (spec 041, FR-017). Meta rechaza un hueco con saltos de linea, tabuladores, mas de cuatro espacios
 * seguidos o vacio; un titulo o una direccion escritos a mano pueden traer las tres cosas. Se junta todo en una linea, se recorta a
 * {@link #LARGO_MAXIMO} caracteres y un hueco vacio dice «—».
 */
public final class HuecoDePlantilla {

    public static final int LARGO_MAXIMO = 400;
    static final String VACIO = "—";

    private HuecoDePlantilla() {
    }

    public static String limpiar(String texto) {
        String una = texto == null ? "" : texto.replaceAll("\\s+", " ").strip();
        if (una.isEmpty()) {
            return VACIO;
        }
        return una.length() > LARGO_MAXIMO ? una.substring(0, LARGO_MAXIMO - 1).stripTrailing() + "…" : una;
    }
}
