package com.renaser.os.users.infrastructure.adapter.out.persistence.participante;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataParticipacionProgramaRepository extends JpaRepository<ParticipacionProgramaJpaEntity, UUID> {
}
