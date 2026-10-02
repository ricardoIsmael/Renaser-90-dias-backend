package com.renaser.os.community.application.ports.out.publicacion;

import com.renaser.os.community.domain.model.publicacion.Comentario;
import com.renaser.os.community.domain.model.publicacion.ComentarioId;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LoadComentarioPort {

    Optional<Comentario> porId(ComentarioId id);

    /** Cronologico ASCENDENTE (al reves que el feed): una conversacion se lee de arriba
     * abajo (wall/repository.ts:190-192). {@code cursor} = `creadoEn` del ultimo ya cargado. */
    List<Comentario> pagina(PublicacionId publicacionId, Instant cursor, int limite);

    /**
     * Comentarios visibles de la publicacion, por autor. Por autor y no un total (D-243): el total que
     * se muestra no cuenta a las cuentas cerradas esperando su borrado, y eso lo decide el servicio con
     * {@code users.api.CuentasCerradasFinder}. Un autor sin comentarios visibles no aparece.
     */
    Map<UserId, Integer> contarPorAutor(PublicacionId publicacionId);

    /**
     * Conteo en lote para una pagina entera del feed (E-80). Una publicacion
     * sin comentarios visibles <b>no aparece</b> en el mapa: la ausencia es cero.
     */
    Map<PublicacionId, Integer> contarDeVarias(Collection<PublicacionId> publicacionIds);
}
