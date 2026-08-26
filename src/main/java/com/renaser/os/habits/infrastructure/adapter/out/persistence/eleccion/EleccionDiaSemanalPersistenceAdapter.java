package com.renaser.os.habits.infrastructure.adapter.out.persistence.eleccion;

import com.renaser.os.habits.application.ports.out.eleccion.LoadEleccionDiaSemanalPort;
import com.renaser.os.habits.application.ports.out.eleccion.SaveEleccionDiaSemanalPort;
import com.renaser.os.habits.domain.model.eleccion.EleccionDiaSemanal;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
class EleccionDiaSemanalPersistenceAdapter implements LoadEleccionDiaSemanalPort, SaveEleccionDiaSemanalPort {

    private final SpringDataEleccionDiaSemanalRepository repository;
    private final EleccionDiaSemanalPersistenceMapper mapper;

    EleccionDiaSemanalPersistenceAdapter(SpringDataEleccionDiaSemanalRepository repository,
                                          EleccionDiaSemanalPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<EleccionDiaSemanal> deHabitoEnSemana(UserId participanteId, HabitoId habitoId,
                                                       LocalDate semanaInicio) {
        return repository.findByParticipanteIdAndHabitoIdAndSemanaInicio(participanteId.value(), habitoId.value(),
                semanaInicio).stream().map(mapper::toDomain).toList();
    }

    @Override
    public EleccionDiaSemanal save(EleccionDiaSemanal eleccion) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(eleccion)));
    }

    @Override
    public void borrarDeSemana(UserId participanteId, HabitoId habitoId, LocalDate semanaInicio) {
        repository.deleteByParticipanteIdAndHabitoIdAndSemanaInicio(participanteId.value(), habitoId.value(),
                semanaInicio);
    }
}
