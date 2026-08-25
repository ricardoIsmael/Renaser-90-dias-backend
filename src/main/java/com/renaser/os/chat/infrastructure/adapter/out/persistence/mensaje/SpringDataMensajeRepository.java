package com.renaser.os.chat.infrastructure.adapter.out.persistence.mensaje;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringDataMensajeRepository extends JpaRepository<MensajeJpaEntity, UUID> {

    /**
     * Dos metodos (con/sin cursor) en vez de {@code (:cursor IS NULL OR ...)} — mismo
     * defecto E-31 que ya documento `community` (Postgres no puede inferir el tipo de un
     * parametro que solo aparece en {@code ? IS NULL}), ver
     * {@code SpringDataPublicacionRepository} como plantilla.
     */
    @Query("""
            SELECT m FROM MensajeJpaEntity m
            WHERE m.conversacionId = :conversacionId
            ORDER BY m.creadoEn DESC
            """)
    List<MensajeJpaEntity> paginaSinCursor(@Param("conversacionId") UUID conversacionId, Pageable pageable);

    @Query("""
            SELECT m FROM MensajeJpaEntity m
            WHERE m.conversacionId = :conversacionId
              AND m.creadoEn < :cursor
            ORDER BY m.creadoEn DESC
            """)
    List<MensajeJpaEntity> paginaConCursor(@Param("conversacionId") UUID conversacionId,
                                            @Param("cursor") Instant cursor, Pageable pageable);

    /** Ultimo mensaje por conversacion EN UNA SOLA consulta (nunca N+1 — CLAUDE.MD del
     * encargo), via {@code DISTINCT ON} de Postgres. */
    @Query(value = """
            SELECT DISTINCT ON (conversacion_id) *
            FROM renaser.mensajes
            WHERE conversacion_id IN (:conversacionIds)
            ORDER BY conversacion_id, creado_en DESC
            """, nativeQuery = true)
    List<MensajeJpaEntity> ultimosPorConversacion(@Param("conversacionIds") List<UUID> conversacionIds);
}
