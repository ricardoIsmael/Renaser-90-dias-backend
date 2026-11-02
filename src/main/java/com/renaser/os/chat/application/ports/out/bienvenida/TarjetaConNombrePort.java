package com.renaser.os.chat.application.ports.out.bienvenida;

import com.renaser.os.shared.domain.Formacion;

/**
 * La tarjeta de Canva con un primer nombre, lista para servir como foto (D-205): la imagen y una huella
 * que cambia si y solo si cambia la imagen. Dibujarla cuesta; quien la implementa decide cuánto la
 * guarda.
 */
public interface TarjetaConNombrePort {

    /** La tarjeta con ese primer nombre sobre la portada vigente de {@code formacion} (spec 035). */
    TarjetaConNombre tarjetaDe(String primerNombre, Formacion formacion);

    /**
     * @param jpeg   la tarjeta en {@link DibujarBienvenidaPort#TIPO_CONTENIDO}; compartida, no se modifica
     * @param huella resumen del contenido: sirve de ETag
     */
    record TarjetaConNombre(byte[] jpeg, String huella) {
    }
}
