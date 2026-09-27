package com.renaser.os.chat.application.ports.out.bienvenida;

/**
 * La tarjeta de Canva con un primer nombre, lista para servir como foto (D-205): la imagen y una huella
 * que cambia si y solo si cambia la imagen. Dibujarla cuesta; quien la implementa decide cuánto la
 * guarda.
 */
public interface TarjetaConNombrePort {

    TarjetaConNombre tarjetaDe(String primerNombre);

    /**
     * @param jpeg   la tarjeta en {@link DibujarBienvenidaPort#TIPO_CONTENIDO}; compartida, no se modifica
     * @param huella resumen del contenido: sirve de ETag
     */
    record TarjetaConNombre(byte[] jpeg, String huella) {
    }
}
