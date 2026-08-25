package com.renaser.os.community.application.ports.out.publicacion;

import com.renaser.os.community.domain.model.publicacion.Publicacion;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoadPublicacionPort {

    Optional<Publicacion> porId(PublicacionId id);

    /** {@code cursor} = `creadoEn` de la ultima publicacion ya cargada, null para la primera
     * pagina. Trae {@code limite + 1} para saber si hay mas sin un COUNT aparte
     * (wall/repository.ts:37-39). {@code categoriaClave} null = todas las categorias. */
    List<Publicacion> feed(Instant cursor, int limite, String categoriaClave);

    /** Cola de moderacion — solo `oculta = true` (wall/repository.ts:86-103). */
    List<Publicacion> feedOculto(Instant cursor, int limite);

    int contarPorAutor(UserId autorId);

    Optional<Publicacion> ultimaVisible();
}
