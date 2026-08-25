package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.cuestionario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataPreguntaOnboardingRepository extends JpaRepository<PreguntaOnboardingJpaEntity, Integer> {

    List<PreguntaOnboardingJpaEntity> findBySeccionIdOrderByOrdenAsc(Short seccionId);
}
