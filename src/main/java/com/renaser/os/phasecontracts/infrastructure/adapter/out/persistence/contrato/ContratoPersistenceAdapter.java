package com.renaser.os.phasecontracts.infrastructure.adapter.out.persistence.contrato;

import com.renaser.os.phasecontracts.application.ports.out.contrato.LoadContratoPort;
import com.renaser.os.phasecontracts.application.ports.out.contrato.SaveContratoPort;
import com.renaser.os.phasecontracts.domain.model.contrato.ContratoFase;
import com.renaser.os.phasecontracts.domain.model.contrato.FasePrograma;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class ContratoPersistenceAdapter implements LoadContratoPort, SaveContratoPort {

    private final SpringDataContratoRepository repository;
    private final ContratoPersistenceMapper mapper;

    ContratoPersistenceAdapter(SpringDataContratoRepository repository, ContratoPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<ContratoFase> porParticipanteYFase(UserId participanteId, FasePrograma fase) {
        return repository.findByParticipanteIdAndFase(participanteId.value(), mapper.toJpaFase(fase))
                .map(mapper::toDomain);
    }

    @Override
    public List<ContratoFase> todosDeParticipante(UserId participanteId) {
        return repository.findByParticipanteIdOrderByFirmadoEnAsc(participanteId.value()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public ContratoFase save(ContratoFase contrato) {
        var saved = repository.saveAndFlush(mapper.toEntity(contrato));
        return mapper.toDomain(saved);
    }
}
