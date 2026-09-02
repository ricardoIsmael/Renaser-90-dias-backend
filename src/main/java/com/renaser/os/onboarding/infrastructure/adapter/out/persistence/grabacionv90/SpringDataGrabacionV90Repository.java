package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.grabacionv90;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataGrabacionV90Repository extends JpaRepository<GrabacionV90JpaEntity, Long> {

    Optional<GrabacionV90JpaEntity> findByUsuarioIdAndFaseAndEjeAndIndice(UUID usuarioId, String fase, String eje,
                                                                           Short indice);

    /**
     * Bloqueo pesimista para el camino de ESCRITURA (mismo patron que
     * {@code SpringDataRegistroHabitoRepository.findByIdParaEscritura} en {@code habits}).
     * Sin el, dos {@code POST .../validation} concurrentes sobre la misma grabacion leian
     * ambas {@code PENDIENTE}, ambas pasaban el guard en memoria del dominio y ambas
     * disparaban su propia llamada a la IA (C-3).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GrabacionV90JpaEntity g WHERE g.id = :id")
    Optional<GrabacionV90JpaEntity> findByIdParaEscritura(@Param("id") Long id);

    List<GrabacionV90JpaEntity> findByUsuarioId(UUID usuarioId);

    long countByEstadoIa(EstadoIAv90Jpa estado);
}
