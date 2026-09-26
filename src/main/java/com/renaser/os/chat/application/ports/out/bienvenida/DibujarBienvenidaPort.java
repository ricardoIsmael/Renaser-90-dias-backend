package com.renaser.os.chat.application.ports.out.bienvenida;

/**
 * La tarjeta de bienvenida de Operaciones (el Canva "Programa Formación Renaser") con el nombre
 * de la persona escrito encima (D-174).
 */
public interface DibujarBienvenidaPort {

    String TIPO_CONTENIDO = "image/jpeg";

    /** @return la tarjeta en {@link #TIPO_CONTENIDO}; con {@code nombre} vacío, el fondo solo. */
    byte[] dibujar(String nombre);
}
