package com.renaser.os.habits.infrastructure.adapter.out.persistence.audioterapia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataAudioterapiaRepository extends JpaRepository<AudioterapiaJpaEntity, Integer> {

    List<AudioterapiaJpaEntity> findAllByOrderBySemanaAsc();
}
