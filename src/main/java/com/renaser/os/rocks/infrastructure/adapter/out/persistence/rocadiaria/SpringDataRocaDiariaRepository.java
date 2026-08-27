package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocadiaria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataRocaDiariaRepository extends JpaRepository<RocaDiariaJpaEntity, UUID> {

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
