package com.renaser.os.chat.infrastructure.adapter.out.persistence.conversacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataConversacionRepository extends JpaRepository<ConversacionJpaEntity, UUID> {

    Optional<ConversacionJpaEntity> findByClaveDirecta(String claveDirecta);

    Optional<ConversacionJpaEntity> findByCelulaId(UUID celulaId);

    /** Unica por `conversacion_global_unica_uk` (indice parcial, V1__baseline_renaser.sql:1292). */
    Optional<ConversacionJpaEntity> findFirstByTipo(TipoConversacionJpa tipo);

    List<ConversacionJpaEntity> findByIdIn(List<UUID> ids);
}
