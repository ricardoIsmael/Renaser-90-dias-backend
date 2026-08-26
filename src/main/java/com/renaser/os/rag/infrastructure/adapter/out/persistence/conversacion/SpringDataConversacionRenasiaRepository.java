package com.renaser.os.rag.infrastructure.adapter.out.persistence.conversacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataConversacionRenasiaRepository extends JpaRepository<ConversacionRenasiaJpaEntity, UUID> {
}
