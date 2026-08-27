package com.renaser.os.chat.infrastructure.adapter.out.persistence.conversacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface SpringDataParticipanteConversacionRepository
        extends JpaRepository<ParticipanteConversacionJpaEntity, ParticipanteConversacionId> {

    boolean existsByConversacionIdAndUsuarioId(UUID conversacionId, UUID usuarioId);

    @Query("SELECT p.conversacionId FROM ParticipanteConversacionJpaEntity p WHERE p.usuarioId = :usuarioId")
    List<UUID> conversacionIdsDeUsuario(@Param("usuarioId") UUID usuarioId);

    @Query("SELECT p.usuarioId FROM ParticipanteConversacionJpaEntity p WHERE p.conversacionId = :conversacionId")
    List<UUID> usuarioIdsDeConversacion(@Param("conversacionId") UUID conversacionId);

    /**
     * Conteo de no-leidos EN UNA SOLA consulta por lote (nunca N+1 — CLAUDE.MD del
     * encargo): un mensaje cuenta como no-leido si es mas reciente que
     * {@code ultimo_leido_en} del participante, o si el participante nunca marco lectura.
     */
    @Query(value = """
            SELECT pc.conversacion_id AS conversacionId, COUNT(m.id) AS conteo
            FROM renaser.participantes_conversacion pc
            JOIN renaser.mensajes m ON m.conversacion_id = pc.conversacion_id
                AND (pc.ultimo_leido_en IS NULL OR m.creado_en > pc.ultimo_leido_en)
            WHERE pc.usuario_id = :usuarioId AND pc.conversacion_id IN (:conversacionIds)
            GROUP BY pc.conversacion_id
            """, nativeQuery = true)
    List<ConteoNoLeidoProjection> contarNoLeidos(@Param("usuarioId") UUID usuarioId,
                                                  @Param("conversacionIds") List<UUID> conversacionIds);
}
