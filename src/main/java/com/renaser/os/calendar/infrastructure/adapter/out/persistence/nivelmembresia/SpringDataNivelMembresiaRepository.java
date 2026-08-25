package com.renaser.os.calendar.infrastructure.adapter.out.persistence.nivelmembresia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataNivelMembresiaRepository extends JpaRepository<NivelMembresiaJpaEntity, Short> {

    List<NivelMembresiaJpaEntity> findAllByOrderByRangoAsc();
}
