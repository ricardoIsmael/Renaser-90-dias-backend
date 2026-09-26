package com.renaser.os.chat.infrastructure.adapter.out.persistence.conversacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataConversacionRepository extends JpaRepository<ConversacionJpaEntity, UUID> {

    Optional<ConversacionJpaEntity> findByClaveDirecta(String claveDirecta);

    Optional<ConversacionJpaEntity> findByCelulaId(UUID celulaId);

    /** Unica por `conversacion_global_unica_uk` (indice parcial, V1__baseline_renaser.sql:1292). */
    Optional<ConversacionJpaEntity> findFirstByTipo(TipoConversacionJpa tipo);

    /** Todas las de un tipo. Hoy solo la usa SOPORTE, que tiene una por aprendiz del padron. */
    List<ConversacionJpaEntity> findByTipo(TipoConversacionJpa tipo);

    List<ConversacionJpaEntity> findByIdIn(List<UUID> ids);

    /** Solo la clave, no la fila: quien pregunta quiere saber cuales faltan, no leerlas. */
    @Query("select c.claveDirecta from ConversacionJpaEntity c where c.claveDirecta in :claves")
    List<String> clavesDirectasEntre(@Param("claves") Collection<String> claves);
}
