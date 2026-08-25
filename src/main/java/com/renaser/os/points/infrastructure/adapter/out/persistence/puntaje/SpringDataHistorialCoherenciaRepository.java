package com.renaser.os.points.infrastructure.adapter.out.persistence.puntaje;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataHistorialCoherenciaRepository
        extends JpaRepository<HistorialCoherenciaJpaEntity, HistorialCoherenciaId> {
}
