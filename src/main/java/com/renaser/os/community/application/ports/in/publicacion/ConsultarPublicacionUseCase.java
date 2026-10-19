package com.renaser.os.community.application.ports.in.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarFeedUseCase.MediaFirmada;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * UNA publicación del Muro por su id, para «Ver publicación» en la ficha del líder, la del mentor y la de administración
 * (spec 005, R-6): el post diario se acredita con la publicación de ese día y quien revisa a la persona tiene que poder
 * abrirla.
 *
 * <p>No es el feed: lo lee quien corresponde ({@code ConsultarPublicacionService}) y trae solo lo que se muestra, sin
 * reacciones ni comentarios.
 */
public interface ConsultarPublicacionUseCase {

    /**
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si la cuenta está suspendida o no le corresponde
     *                                                             leerla
     * @throws java.util.NoSuchElementException                    si no existe, está oculta o borrada, o es de una cuenta cerrada
     */
    PublicacionParaLeer publicacionDe(UserId actorId, PublicacionId publicacionId);

    /** @param media sus archivos con la URL firmada (vence a los 15 minutos), en el orden del carrusel */
    record PublicacionParaLeer(UUID id, UserId autorId, String autorNombre, String autorAvatarUrl, String texto,
                               Instant creadaEn, List<MediaFirmada> media) {

        public PublicacionParaLeer {
            media = media == null ? List.of() : List.copyOf(media);
        }
    }
}
