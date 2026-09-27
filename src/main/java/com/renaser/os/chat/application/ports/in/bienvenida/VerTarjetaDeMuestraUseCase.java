package com.renaser.os.chat.application.ports.in.bienvenida;

import com.renaser.os.shared.domain.UserId;

/**
 * La vista previa de la tarjeta de bienvenida con un nombre de ejemplo (D-210): sobre la portada vigente
 * o sobre una candidata recién subida, antes de confirmarla. Solo ADMIN y ALCHEMIST con la cuenta activa.
 */
public interface VerTarjetaDeMuestraUseCase {

    /**
     * @param nombre           el nombre de ejemplo; se usa su primera palabra, como con una persona de
     *                         verdad. Vacío: la portada sola
     * @param portadaCandidata {@code null} para la vigente, o la ruta de una recién subida (se revisa)
     * @return la tarjeta en JPEG
     * @throws IllegalArgumentException si el nombre es muy largo o la candidata no sirve (con el motivo)
     * @throws java.util.NoSuchElementException si no hay nada subido en la ruta candidata
     * @throws IllegalStateException si pide una candidata y el servidor no guarda imágenes
     */
    byte[] muestra(UserId actorId, String nombre, String portadaCandidata);
}
