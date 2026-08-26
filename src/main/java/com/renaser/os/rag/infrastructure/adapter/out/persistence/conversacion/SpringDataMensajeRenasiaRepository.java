package com.renaser.os.rag.infrastructure.adapter.out.persistence.conversacion;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringDataMensajeRenasiaRepository extends JpaRepository<MensajeRenasiaJpaEntity, UUID> {

    /** Dos metodos (con/sin cursor) en vez de {@code (:cursor IS NULL OR ...)} — mismo
     * defecto E-31 documentado por `community`/`chat` (Postgres no infiere el tipo de un
     * parametro que solo aparece en {@code ? IS NULL}). */
    @Query("""
            SELECT m FROM MensajeRenasiaJpaEntity m
            WHERE m.usuarioId = :usuarioId
            ORDER BY m.creadoEn DESC
            """)
    List<MensajeRenasiaJpaEntity> paginaSinCursor(@Param("usuarioId") UUID usuarioId, Pageable pageable);

    @Query("""
            SELECT m FROM MensajeRenasiaJpaEntity m
            WHERE m.usuarioId = :usuarioId
              AND m.creadoEn < :cursor
            ORDER BY m.creadoEn DESC
            """)
    List<MensajeRenasiaJpaEntity> paginaConCursor(@Param("usuarioId") UUID usuarioId, @Param("cursor") Instant cursor,
                                                   Pageable pageable);
}
