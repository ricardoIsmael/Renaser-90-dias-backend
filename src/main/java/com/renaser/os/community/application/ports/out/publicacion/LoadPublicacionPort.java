package com.renaser.os.community.application.ports.out.publicacion;

import com.renaser.os.community.api.PublicacionMuroFinder;

import com.renaser.os.community.domain.model.publicacion.Publicacion;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoadPublicacionPort {

    Optional<Publicacion> porId(PublicacionId id);

    /** {@code alcance} = las formaciones que lee quien pide (spec 024): el Muro de una formación no mezcla las demás.
     * {@code cursor} = `creadoEn` de la ultima publicacion ya cargada, null para la primera
     * pagina. Trae {@code limite + 1} para saber si hay mas sin un COUNT aparte
     * (wall/repository.ts:37-39). {@code categoriaClave} null = todas las categorias. */
    List<Publicacion> feed(Instant cursor, int limite, String categoriaClave, AlcanceDeFormacion alcance);

    /** Cola de moderacion — solo `oculta = true` (wall/repository.ts:86-103), de las formaciones del alcance (spec 024). */
    List<Publicacion> feedOculto(Instant cursor, int limite, AlcanceDeFormacion alcance);

    /** Cuántas publicaciones propias tiene {@code autorId} en las formaciones del alcance. */
    int contarPorAutor(UserId autorId, AlcanceDeFormacion alcance);

    /**
     * Si el autor tiene al menos una publicacion creada en {@code [desde, hasta)}. Es una
     * pregunta de existencia, no un conteo: se responde con un {@code EXISTS} que corta en
     * la primera fila, porque el unico consumidor ({@code community.api.PublicacionMuroFinder},
     * para el habito POST DIARIO EN COMUNIDAD) solo necesita el si/no y esto corre en el
     * camino de completar un habito.
     *
     * <p>Incluye las publicaciones {@code oculta = true} — el motivo esta en el javadoc del
     * puerto publico, que es donde vive esa decision.
     */
    boolean existeDeAutorEntre(UserId autorId, Instant desde, Instant hasta);

    /** Las del autor en {@code [desde, hasta)}, ocultas incluidas, de la más vieja a la más nueva (E-628). */
    List<PublicacionMuroFinder.PublicacionDelAutor> deAutorEntre(UserId autorId, Instant desde, Instant hasta);

    Optional<Publicacion> ultimaVisible(AlcanceDeFormacion alcance);
}
