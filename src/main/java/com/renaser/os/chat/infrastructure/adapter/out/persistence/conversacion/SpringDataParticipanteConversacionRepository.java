package com.renaser.os.chat.infrastructure.adapter.out.persistence.conversacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringDataParticipanteConversacionRepository
        extends JpaRepository<ParticipanteConversacionJpaEntity, ParticipanteConversacionId> {

    boolean existsByConversacionIdAndUsuarioId(UUID conversacionId, UUID usuarioId);

    /**
     * La marca de lectura solo avanza (D-208): de ella sale el ✓✓, y un ✓✓ no puede volver a ✓. Antes
     * era leer la fila, cambiarla y guardarla: dos lecturas a la vez (el teléfono y la web) podían
     * pisarse y dejar la marca más vieja. Un solo UPDATE condicionado no tiene esa carrera.
     *
     * <p>{@code flushAutomatically}: dentro de {@code MensajeService.enviar} corre después de guardar el
     * mensaje, y un UPDATE masivo no espera a lo pendiente del contexto. Sin {@code clearAutomatically}
     * a propósito: vaciar el contexto descarta lo no escrito (la trampa que ya se vio en
     * {@code ConfirmacionPersistenceAdapter}), y nadie relee esta entidad en la misma transacción: la
     * marca se lee con {@link #marcasDeLectura}, que es una proyección y va a la base.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE ParticipanteConversacionJpaEntity p SET p.ultimoLeidoEn = :ahora
            WHERE p.conversacionId = :conversacionId AND p.usuarioId = :usuarioId
              AND (p.ultimoLeidoEn IS NULL OR p.ultimoLeidoEn < :ahora)
            """)
    int marcarLeidoSiAvanza(@Param("conversacionId") UUID conversacionId, @Param("usuarioId") UUID usuarioId,
                            @Param("ahora") Instant ahora);

    /**
     * Los participantes de UNA conversación con su marca de lectura (D-208), en una consulta sobre la
     * clave primaria {@code (conversacion_id, usuario_id)}: no hizo falta índice nuevo.
     */
    @Query("""
            SELECT p.usuarioId AS usuarioId, p.ultimoLeidoEn AS ultimoLeidoEn, p.creadoEn AS creadoEn
            FROM ParticipanteConversacionJpaEntity p
            WHERE p.conversacionId = :conversacionId
            """)
    List<MarcaDeLecturaProjection> marcasDeLectura(@Param("conversacionId") UUID conversacionId);

    interface MarcaDeLecturaProjection {
        UUID getUsuarioId();

        Instant getUltimoLeidoEn();

        Instant getCreadoEn();
    }

    @Query("SELECT p.conversacionId FROM ParticipanteConversacionJpaEntity p WHERE p.usuarioId = :usuarioId")
    List<UUID> conversacionIdsDeUsuario(@Param("usuarioId") UUID usuarioId);

    @Query("SELECT p.usuarioId FROM ParticipanteConversacionJpaEntity p WHERE p.conversacionId = :conversacionId")
    List<UUID> usuarioIdsDeConversacion(@Param("conversacionId") UUID conversacionId);

    /**
     * Los participantes que NO son el actor, en las conversaciones indicadas. La proyeccion trae
     * el par (conversacion, usuario) porque quien llama necesita saber a que fila pertenece cada
     * nombre; devolver solo los usuarios obligaria a una segunda consulta para reconstruirlo.
     */
    @Query("""
            SELECT p.conversacionId AS conversacionId, p.usuarioId AS usuarioId
            FROM ParticipanteConversacionJpaEntity p
            WHERE p.conversacionId IN :conversacionIds AND p.usuarioId <> :actorId
            """)
    List<OtroParticipanteProjection> otrosParticipantes(@Param("conversacionIds") List<UUID> conversacionIds,
                                                         @Param("actorId") UUID actorId);

    interface OtroParticipanteProjection {
        UUID getConversacionId();

        UUID getUsuarioId();
    }

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
