package com.renaser.os.community.infrastructure.adapter.out.persistence.publicacion;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface SpringDataComentarioRepository extends JpaRepository<ComentarioJpaEntity, UUID> {

    /** Cronologico ASCENDENTE — al reves que el feed (wall/repository.ts:190-204).
     *
     * <p>E-31 (docs/BITACORA_ERRORES.md): el mismo defecto que en
     * {@link SpringDataPublicacionRepository} — {@code (:cursor IS NULL OR c.creadoEn > :cursor)}
     * rompia contra Postgres en la primera pagina (sin cursor). Mismo arreglo: metodo por
     * combinacion de filtro opcional, sin CAST ni query nativa. */
    @Query("""
            SELECT c FROM ComentarioJpaEntity c
            WHERE c.publicacionId = :publicacionId AND c.oculto = false
            ORDER BY c.creadoEn ASC
            """)
    List<ComentarioJpaEntity> paginaSinCursor(@Param("publicacionId") UUID publicacionId, Pageable pageable);

    @Query("""
            SELECT c FROM ComentarioJpaEntity c
            WHERE c.publicacionId = :publicacionId AND c.oculto = false
              AND c.creadoEn > :cursor
            ORDER BY c.creadoEn ASC
            """)
    List<ComentarioJpaEntity> paginaConCursor(@Param("publicacionId") UUID publicacionId,
                                               @Param("cursor") Instant cursor, Pageable pageable);

    long countByPublicacionIdAndOcultoFalse(UUID publicacionId);

    /** Conteo en lote para una pagina entera del feed (E-80): una sola consulta agrupada en vez
     * de una por publicacion. Devuelve solo las publicaciones QUE TIENEN al menos un comentario
     * visible — las que no aparecen valen cero, y de eso se encarga quien consume. */
    @Query("""
            SELECT c.publicacionId, COUNT(c) FROM ComentarioJpaEntity c
            WHERE c.publicacionId IN :publicacionIds AND c.oculto = false
            GROUP BY c.publicacionId
            """)
    List<Object[]> contarPorPublicacion(@Param("publicacionIds") Collection<UUID> publicacionIds);
}
