package com.renaser.os.community.application.ports.in.publicacion;

import com.renaser.os.community.domain.model.publicacion.Publicacion;
import com.renaser.os.community.domain.model.publicacion.TipoReaccion;
import com.renaser.os.shared.domain.UserId;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConsultarFeedUseCase {

    /** {@code categoriaClave} null = todas. Cursor-paginado, mas nueva primero
     * (wall/service.ts:229-270). */
    PaginaPublicaciones feed(UserId actorId, Instant cursor, String categoriaClave);

    /** Cola de moderacion — solo ADMIN/ALCHEMIST (wall/service.ts:283-310). */
    PaginaPublicaciones feedOculto(UserId actorId, Instant cursor);

    int contarMisPublicaciones(UserId actorId);

    /** Nombre de quien publico lo mas reciente visible — invitacion a Comunidad en Inicio
     * (wall/service.ts:272-281). Pide el actor porque devuelve el NOMBRE COMPLETO de otra
     * persona: es una lectura del Muro y exige lo mismo que {@link #feed} (cuenta activa).
     * Ver E-50 en docs/BITACORA_ERRORES.md. */
    Optional<String> ultimoAutor(UserId actorId);

    record PaginaPublicaciones(List<PublicacionVista> publicaciones, Instant siguienteCursor) {
    }

    record PublicacionVista(Publicacion publicacion, String autorNombre, String autorAvatarUrl, int likes,
                             int dislikes, TipoReaccion miReaccion, int cantidadComentarios,
                             List<MediaFirmada> media) {
    }

    record MediaFirmada(URI url, String mime, int orden) {
    }
}
