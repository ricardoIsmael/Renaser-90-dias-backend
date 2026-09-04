package com.renaser.os.users.infrastructure.adapter.out.persistence.ajustediaprograma;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataAjusteDiaProgramaRepository extends JpaRepository<AjusteDiaProgramaJpaEntity, UUID> {

    /** Apoyado en `ajustes_dia_programa_participante_idx (participante_id, ajustado_en DESC)`. */
    Optional<AjusteDiaProgramaJpaEntity> findFirstByParticipanteIdOrderByAjustadoEnDesc(UUID participanteId);
}
