package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.cuestionario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataSeccionOnboardingRepository extends JpaRepository<SeccionOnboardingJpaEntity, Short> {

    List<SeccionOnboardingJpaEntity> findByFlujoOrderByOrdenAsc(String flujo);
}
