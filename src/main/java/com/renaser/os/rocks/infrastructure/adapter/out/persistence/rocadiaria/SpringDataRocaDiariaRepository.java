package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocadiaria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;

interface SpringDataRocaDiariaRepository extends JpaRepository<RocaDiariaJpaEntity, UUID> {

    /**
     * Bloqueo pesimista para el camino de ESCRITURA (mismo patron que
     * {@code SpringDataRegistroHabitoRepository.findByIdParaEscritura}, que a su vez espeja
     * {@code SpringDataPuntajeParticipanteRepository.findByIdParaEscritura}). Sin el, un doble
     * toque o un reintento por timeout de red hacian que dos requests concurrentes leyeran la
     * misma roca con {@code completada = false}, ambas pasaran la validacion en memoria y ambas
     * completaran: doble evidencia, doble premio, dos {@code RocaCompletadaEvent} (C-2,
     * docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RocaDiariaJpaEntity r WHERE r.id = :id")
    Optional<RocaDiariaJpaEntity> findByIdParaEscritura(@Param("id") UUID id);

    List<RocaDiariaJpaEntity> findByParticipanteIdAndFecha(UUID participanteId, LocalDate fecha);

    int countByParticipanteIdAndFecha(UUID participanteId, LocalDate fecha);

    int countByParticipanteIdAndCompletadaTrue(UUID participanteId);

    @Query("select min(r.completadaEn) from RocaDiariaJpaEntity r "
            + "where r.participanteId = :participanteId and r.completada = true")
    Optional<Instant> primeraCompletadaEnDeParticipante(@Param("participanteId") UUID participanteId);

    @Query("select r.fecha from RocaDiariaJpaEntity r "
            + "where r.participanteId = :participanteId and r.completada = true")
    List<LocalDate> fechasCompletadasDeParticipante(@Param("participanteId") UUID participanteId);
}
