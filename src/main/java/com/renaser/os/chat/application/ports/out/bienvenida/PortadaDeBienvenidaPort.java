package com.renaser.os.chat.application.ports.out.bienvenida;

/**
 * Las portadas que sube Administración para la tarjeta de bienvenida (D-210): abrirlas y revisarlas
 * antes de que se usen.
 */
public interface PortadaDeBienvenidaPort {

    /**
     * Trae la imagen subida en {@code ruta}, la revisa (formato, peso, medidas y que el nombre se lea
     * encima; {@link com.renaser.os.chat.domain.model.bienvenida.PortadaDeBienvenida}) y la deja lista para
     * dibujar con {@link DibujarBienvenidaPort#dibujar(String, String)}.
     *
     * <p>Abre la imagen: no se llama dentro de una transacción.
     *
     * @throws IllegalArgumentException si la imagen no sirve, con el motivo en palabras simples
     * @throws java.util.NoSuchElementException si no hay nada subido en esa ruta
     */
    void revisar(String ruta);
}
