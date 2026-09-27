package com.renaser.os.chat.application.ports.in.bienvenida;

import com.renaser.os.shared.domain.UserId;

import java.net.URI;

/**
 * Cambiar la portada de la tarjeta de bienvenida (D-210), con el mismo camino en tres pasos que la
 * portada de un evento (D-186): URL prefirmada, el teléfono sube la imagen directo al almacenamiento, y
 * se confirma la ruta. Solo ADMIN y ALCHEMIST con la cuenta activa.
 */
public interface CambiarPortadaDeBienvenidaUseCase {

    /**
     * Con el almacenamiento de marcador la URL sale {@code about:blank#pendiente-s3/...}, como en el
     * resto de la app: el teléfono lo detecta y avisa.
     *
     * @throws IllegalArgumentException si el tipo no es JPEG ni PNG
     */
    UrlDeSubida solicitarSubida(UserId actorId, String tipoContenido);

    /**
     * Revisa la imagen subida y la deja vigente. Confirmar la que ya es vigente no cambia nada.
     *
     * @throws IllegalArgumentException si la imagen no sirve, con el motivo
     * @throws java.util.NoSuchElementException si no hay nada subido en esa ruta
     * @throws IllegalStateException si el servidor no guarda imágenes
     */
    BienvenidaEditable confirmar(UserId actorId, String ruta);

    /** Si ya salía la original, no cambia nada. */
    BienvenidaEditable volverALaOriginal(UserId actorId);

    /** @param ruta la que hay que mandar después a {@link #confirmar} */
    record UrlDeSubida(URI url, String ruta) {
    }
}
