package com.renaser.os.chat.application.ports.in.bienvenida;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;


/**
 * Ver y cambiar la imagen de una formación en el chat desde la app (spec 035), con el mismo camino en tres pasos que la portada de la bienvenida:
 * URL prefirmada, el teléfono sube la imagen directo al almacenamiento y se confirma la ruta. Solo ADMIN y ALCHEMIST con la cuenta activa.
 */
public interface AdministrarImagenDeLaFormacionUseCase {

    /** @throws com.renaser.os.shared.domain.NotAuthorizedException si no es ADMIN/ALCHEMIST activo (403) */
    ImagenEditable ver(UserId actorId, Formacion formacion);

    /**
     * @throws IllegalArgumentException si el tipo no es JPEG ni PNG
     */
    CambiarPortadaDeBienvenidaUseCase.UrlDeSubida solicitarSubida(UserId actorId, String tipoContenido);

    /**
     * Revisa la imagen subida y la deja vigente para {@code formacion}. Confirmar la que ya es vigente no cambia nada.
     *
     * @throws IllegalArgumentException si la imagen no sirve, con el motivo
     * @throws java.util.NoSuchElementException si no hay nada subido en esa ruta
     * @throws IllegalStateException si el servidor no guarda imágenes
     */
    ImagenEditable confirmar(UserId actorId, Formacion formacion, String ruta);

    /** La Escuela vuelve a la imagen de la app; las otras, a la de la Escuela. Si ya salía, no cambia nada. */
    ImagenEditable volverALaOriginal(UserId actorId, Formacion formacion);

    /**
     * La imagen como se vería en el chat: la vigente de {@code formacion} o la candidata recién subida (se revisa).
     *
     * @return {@code null} si no hay imagen vigente y no se pidió una candidata (la app muestra la suya)
     */
    byte[] vista(UserId actorId, Formacion formacion, String rutaCandidata);

    /**
     * @param hayImagen       si hay una imagen subida en efecto (propia o la de la Escuela)
     * @param cambiada        si la formación tiene una propia
     * @param heredada        si usa la de la Escuela (siempre {@code false} en la Escuela)
     * @param sePuedeCambiar  {@code false} si el servidor no guarda imágenes (almacenamiento de marcador)
     * @param ultimoCambio    {@code null} si nunca se cambió
     */
    record ImagenEditable(Formacion formacion, boolean hayImagen, boolean cambiada, boolean heredada,
                          boolean sePuedeCambiar, BienvenidaEditable.UltimoCambio ultimoCambio) {
    }
}
