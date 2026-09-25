package com.renaser.os.rag.infrastructure.adapter.out.persistence.memoria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataMemoriaRenasiaRepository extends JpaRepository<MemoriaRenasiaJpaEntity, UUID> {
}
