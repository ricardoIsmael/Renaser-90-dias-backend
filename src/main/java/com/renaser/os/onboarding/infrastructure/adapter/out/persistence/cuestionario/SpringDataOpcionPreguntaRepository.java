package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.cuestionario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataOpcionPreguntaRepository extends JpaRepository<OpcionPreguntaJpaEntity, OpcionPreguntaId> {

    List<OpcionPreguntaJpaEntity> findByPreguntaIdOrderByOrdenAsc(Integer preguntaId);
}
