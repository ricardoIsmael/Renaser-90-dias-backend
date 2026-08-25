package com.renaser.os.habits.infrastructure.adapter.out.persistence.santuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataSesionBloqueoRepository extends JpaRepository<SesionBloqueoJpaEntity, UUID> {
}
