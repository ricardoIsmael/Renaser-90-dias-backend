package com.renaser.os.chat.application.ports.out.caja;

/**
 * La carta con el nombre que va dentro de la Caja Renaser (D-219), lista para imprimir.
 */
public interface DibujarCartaDeCajaPort {

    String TIPO_CONTENIDO = "image/png";

    /**
     * @param fondo {@code null} = el fondo original; si no, la ruta de un fondo ya revisado con {@link #revisar}
     *              (si ya no abre, se dibuja sobre el original)
     * @return la carta en PNG
     */
    byte[] dibujar(String nombre, String fondo);

    /**
     * Trae el fondo subido en {@code ruta} y lo revisa (formato, peso, medidas y que el nombre se lea encima).
     * Abre la imagen: no se llama dentro de una transacción.
     *
     * @throws IllegalArgumentException si la imagen no sirve, con el motivo en palabras simples
     * @throws java.util.NoSuchElementException si no hay nada subido en esa ruta
     */
    void revisar(String ruta);
}
