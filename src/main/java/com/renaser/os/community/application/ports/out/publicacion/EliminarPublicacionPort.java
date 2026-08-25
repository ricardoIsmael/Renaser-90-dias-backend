package com.renaser.os.community.application.ports.out.publicacion;

import com.renaser.os.community.domain.model.publicacion.PublicacionId;

public interface EliminarPublicacionPort {

    /** Borrado fisico, reservado a la cola de moderacion (wall/service.ts:196-208) — las
     * medias/reacciones/comentarios se van en cascada por la FK `ON DELETE CASCADE`
     * (V1__baseline_renaser.sql:1093,1107,1116). */
    void eliminar(PublicacionId id);
}
