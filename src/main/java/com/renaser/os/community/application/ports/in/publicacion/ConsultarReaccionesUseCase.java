package com.renaser.os.community.application.ports.in.publicacion;

import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.community.domain.model.publicacion.TipoReaccion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;

import java.util.List;

/**
 * Quien reacciono a una publicacion del Muro (modal "Reacciones del post" del diseño RN).
 * Misma regla de visibilidad que {@link ReaccionarUseCase}: si la publicacion no es visible
 * para el actor, no hay nada que listar (404); si el actor no existe o esta suspendido, 403 —
 * nunca se filtra quien reacciono a alguien que no puede ver la publicacion.
 */
public interface ConsultarReaccionesUseCase {

    List<ReaccionVista> reacciones(UserId actorId, PublicacionId publicacionId);

    /**
     * Una fila del listado. Deliberadamente NO lleva el email ni ningun otro dato que la
     * pantalla no muestre (CLAUDE.MD §5.4.9/§8): solo lo que el modal pinta — nombre, avatar,
     * rol (para el subtitulo) y el tipo de reaccion.
     *
     * <p>{@code nombre}/{@code avatarUrl}/{@code rol} son {@code null} en el caso raro de que
     * el usuario que reacciono ya no exista en `usuarios` — se resuelve en lote via
     * {@code UserSummaryFinder.findByIds}, que simplemente omite del mapa los ids que no
     * encuentra (nunca lanza), asi que esta vista tiene que tolerar el hueco en vez de asumir
     * que el JOIN siempre completa.
     */
    record ReaccionVista(UserId usuarioId, String nombre, String avatarUrl, UserRole rol, TipoReaccion tipo) {
    }
}
