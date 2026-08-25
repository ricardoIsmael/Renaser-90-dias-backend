package com.renaser.os.academy.infrastructure.adapter.out.persistence.recomendacion;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataRecomendacionAcademiaRepository
        extends JpaRepository<RecomendacionAcademiaJpaEntity, RecomendacionAcademiaId> {
}
