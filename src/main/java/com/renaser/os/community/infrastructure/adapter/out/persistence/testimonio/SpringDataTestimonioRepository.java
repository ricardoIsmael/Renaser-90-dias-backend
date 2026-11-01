package com.renaser.os.community.infrastructure.adapter.out.persistence.testimonio;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface SpringDataTestimonioRepository extends JpaRepository<TestimonioJpaEntity, UUID> {

    List<TestimonioJpaEntity> findByDestacadoTrueAndFormacionInOrderByCreadoEnDesc(Collection<String> formaciones, Pageable pageable);
}
