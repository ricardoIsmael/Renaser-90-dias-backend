package com.renaser.os.habits.infrastructure.adapter.out.persistence.espiritu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataAudiosEspirituRepository extends JpaRepository<AudioEspirituJpaEntity, Integer> {

    List<AudioEspirituJpaEntity> findAllByOrderByDiaAsc();
}
