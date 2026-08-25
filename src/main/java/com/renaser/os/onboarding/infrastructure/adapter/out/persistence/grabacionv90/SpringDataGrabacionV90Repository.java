package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.grabacionv90;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataGrabacionV90Repository extends JpaRepository<GrabacionV90JpaEntity, Long> {

    Optional<GrabacionV90JpaEntity> findByUsuarioIdAndFaseAndEjeAndIndice(UUID usuarioId, String fase, String eje,
                                                                           Short indice);

    List<GrabacionV90JpaEntity> findByUsuarioId(UUID usuarioId);
}
