package com.renaser.os.community.infrastructure.adapter.out.persistence.publicacion;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataPublicacionRepository extends JpaRepository<PublicacionJpaEntity, UUID> {

    /** E-31 (docs/BITACORA_ERRORES.md): Postgres no puede inferir el tipo de un parametro
     * que aparece solo en {@code ? IS NULL} (sin ningun otro contexto tipado) — el patron
     * JPQL {@code (:cursor IS NULL OR col < :cursor)} generaba justo eso y el
     * {@code prepare} fallaba con "could not determine data type of parameter $1" apenas
     * el cliente pedia la pagina sin cursor (el caso mas comun: la primera pagina del
     * feed). Se elige partir en un metodo por combinacion de filtro opcional — SQL simple
     * y explicito por metodo, sin casts ni magia, ideal para el hot path del Muro (feed
     * principal) — en vez de {@code CAST(:cursor AS ...)} o una query nativa con
     * {@code ?::timestamptz}. `feed` tiene DOS filtros opcionales (cursor, categoria) =
     * 4 metodos; el adaptador ({@link PublicacionPersistenceAdapter#feed}) elige cual
     * llamar segun que venga null. */
    @Query("""
            SELECT p FROM PublicacionJpaEntity p
            WHERE p.oculta = false
            ORDER BY p.creadoEn DESC
            """)
    List<PublicacionJpaEntity> feedSinCursorSinCategoria(Pageable pageable);

    @Query("""
            SELECT p FROM PublicacionJpaEntity p
            WHERE p.oculta = false
              AND p.categoriaClave = :categoria
            ORDER BY p.creadoEn DESC
            """)
    List<PublicacionJpaEntity> feedSinCursorConCategoria(@Param("categoria") String categoria, Pageable pageable);

    @Query("""
            SELECT p FROM PublicacionJpaEntity p
            WHERE p.oculta = false
              AND p.creadoEn < :cursor
            ORDER BY p.creadoEn DESC
            """)
    List<PublicacionJpaEntity> feedConCursorSinCategoria(@Param("cursor") Instant cursor, Pageable pageable);

    @Query("""
            SELECT p FROM PublicacionJpaEntity p
            WHERE p.oculta = false
              AND p.creadoEn < :cursor
              AND p.categoriaClave = :categoria
            ORDER BY p.creadoEn DESC
            """)
    List<PublicacionJpaEntity> feedConCursorConCategoria(@Param("cursor") Instant cursor,
                                                           @Param("categoria") String categoria, Pageable pageable);

    /** Mismo defecto (E-31) y misma solucion que {@code feed*} de arriba, para
     * {@code /wall/hidden}. */
    @Query("""
            SELECT p FROM PublicacionJpaEntity p
            WHERE p.oculta = true
            ORDER BY p.creadoEn DESC
            """)
    List<PublicacionJpaEntity> feedOcultoSinCursor(Pageable pageable);

    @Query("""
            SELECT p FROM PublicacionJpaEntity p
            WHERE p.oculta = true
              AND p.creadoEn < :cursor
            ORDER BY p.creadoEn DESC
            """)
    List<PublicacionJpaEntity> feedOcultoConCursor(@Param("cursor") Instant cursor, Pageable pageable);

    long countByAutorId(UUID autorId);

    /**
     * {@code [desde, hasta)} — media ventana. Derivado por nombre de metodo y no con
     * {@code @Query}: no cae en E-31 (los dos parametros se comparan contra una columna
     * tipada, nunca aparecen sueltos en un {@code IS NULL}) y Spring Data lo traduce a un
     * {@code EXISTS} que corta en la primera fila. Lo cubre el indice `muro_autor_idx`
     * sobre `autor_id` (V1).
     *
     * <p>Deliberadamente SIN filtro por `oculta`: ver el javadoc de
     * {@code community.api.PublicacionMuroFinder}.
     */
    boolean existsByAutorIdAndCreadoEnGreaterThanEqualAndCreadoEnLessThan(UUID autorId, Instant desde, Instant hasta);

    Optional<PublicacionJpaEntity> findFirstByOcultaFalseOrderByCreadoEnDesc();
}
