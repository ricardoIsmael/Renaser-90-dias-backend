package com.renaser.os.phasecontracts.infrastructure.adapter.out.persistence.contrato;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataContratoRepository extends JpaRepository<ContratoFaseJpaEntity, UUID> {

    Optional<ContratoFaseJpaEntity> findByParticipanteIdAndFase(UUID participanteId, FaseProgramaJpa fase);

    List<ContratoFaseJpaEntity> findByParticipanteIdOrderByFirmadoEnAsc(UUID participanteId);
}
