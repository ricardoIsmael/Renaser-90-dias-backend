package com.renaser.os.community.application.ports.out.publicacion;

import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.community.domain.model.publicacion.TipoReaccion;
import com.renaser.os.shared.domain.UserId;

import java.util.Map;
import java.util.Optional;

/** Puerto unico para leer y escribir `reacciones_muro` — la fila es tan chica (PK
 * compuesta, sin identidad propia) que separar Load/Save no aporta claridad extra. */
public interface ReaccionMuroPort {

    Optional<TipoReaccion> deUsuario(PublicacionId publicacionId, UserId usuarioId);

    /** Conteo agregado por tipo, listo para {@code Map.of(ME_GUSTA, n, NO_ME_GUSTA, m)}. */
    Map<TipoReaccion, Integer> contarPorTipo(PublicacionId publicacionId);

    void upsert(PublicacionId publicacionId, UserId usuarioId, TipoReaccion tipo);

    void eliminar(PublicacionId publicacionId, UserId usuarioId);
}
