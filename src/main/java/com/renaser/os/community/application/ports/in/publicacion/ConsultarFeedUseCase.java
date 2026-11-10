package com.renaser.os.community.application.ports.in.publicacion;

import com.renaser.os.community.domain.model.publicacion.Publicacion;
import com.renaser.os.community.domain.model.publicacion.TipoReaccion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConsultarFeedUseCase {

    /** {@code categoriaClave} null = todas. Cursor-paginado, mas nueva primero
     * (wall/service.ts:229-270).
     *
     * <p>{@code formacion} (spec 024): el Muro es el de la formación de quien pide. Solo Administración, Alquimista y Soporte TI
     * eligen una; sin elegir leen «Todas», cada publicación con la suya. Pedir una que no se ve da una página vacía. */
    PaginaPublicaciones feed(UserId actorId, Instant cursor, String categoriaClave, Formacion formacion);

    /** Cola de moderacion — solo ADMIN/ALCHEMIST (wall/service.ts:283-310). {@code formacion} null = «Todas». */
    PaginaPublicaciones feedOculto(UserId actorId, Instant cursor, Formacion formacion);

    /** Cuántas publicaciones propias tiene en las formaciones que lee. */
    int contarMisPublicaciones(UserId actorId);

    /** Nombre de quien publico lo mas reciente visible — invitacion a Comunidad en Inicio
     * (wall/service.ts:272-281). Pide el actor porque devuelve el NOMBRE COMPLETO de otra
     * persona: es una lectura del Muro y exige lo mismo que {@link #feed} (cuenta activa).
     * Ver E-50 en docs/BITACORA_ERRORES.md. */
    Optional<String> ultimoAutor(UserId actorId);

    record PaginaPublicaciones(List<PublicacionVista> publicaciones, Instant siguienteCursor) {
    }

    /**
     * @param autorNombre     spec 046: como se le nombra en el Muro (su sobrenombre si tiene uno)
     * @param autorNombreReal spec 046: su nombre real, SOLO para el equipo que acompaña y solo si tiene sobrenombre; si no, {@code null}
     */
    record PublicacionVista(Publicacion publicacion, String autorNombre, String autorAvatarUrl, int likes,
                             int dislikes, TipoReaccion miReaccion, int cantidadComentarios,
                             List<MediaFirmada> media, String autorNombreReal) {

        public PublicacionVista(Publicacion publicacion, String autorNombre, String autorAvatarUrl, int likes, int dislikes,
                                TipoReaccion miReaccion, int cantidadComentarios, List<MediaFirmada> media) {
            this(publicacion, autorNombre, autorAvatarUrl, likes, dislikes, miReaccion, cantidadComentarios, media, null);
        }
    }

    /** {@code miniatura}: la URL firmada de su miniatura (spec 049), o {@code null} si la foto no tiene (video, evidencia de otro módulo). */
    record MediaFirmada(URI url, String mime, int orden, URI miniatura) {

        public MediaFirmada(URI url, String mime, int orden) {
            this(url, mime, orden, null);
        }
    }
}
