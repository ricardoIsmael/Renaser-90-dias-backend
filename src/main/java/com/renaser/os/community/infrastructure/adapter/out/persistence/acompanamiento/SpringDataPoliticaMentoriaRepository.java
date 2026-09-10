package com.renaser.os.community.infrastructure.adapter.out.persistence.acompanamiento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataPoliticaMentoriaRepository extends JpaRepository<PoliticaMentoriaJpaEntity, UUID> {
}
