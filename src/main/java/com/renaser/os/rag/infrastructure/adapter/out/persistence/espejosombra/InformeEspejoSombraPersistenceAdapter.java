package com.renaser.os.rag.infrastructure.adapter.out.persistence.espejosombra;

import com.renaser.os.rag.application.ports.out.espejosombra.LoadInformeEspejoSombraPort;
import com.renaser.os.rag.application.ports.out.espejosombra.SaveInformeEspejoSombraPort;
import com.renaser.os.rag.domain.model.espejosombra.InformeEspejoSombra;
import com.renaser.os.rag.domain.model.espejosombra.InformeEspejoSombraId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
class InformeEspejoSombraPersistenceAdapter implements LoadInformeEspejoSombraPort, SaveInformeEspejoSombraPort {

    private final SpringDataInformeEspejoSombraRepository repository;
    private final InformeEspejoSombraPersistenceMapper mapper;

    InformeEspejoSombraPersistenceAdapter(SpringDataInformeEspejoSombraRepository repository,
                                           InformeEspejoSombraPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<InformeEspejoSombra> byId(InformeEspejoSombraId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<InformeEspejoSombra> porParticipanteYSemana(UserId participanteId, LocalDate semanaInicio) {
        return repository.findByParticipanteIdAndSemanaInicio(participanteId.value(), semanaInicio)
                .map(mapper::toDomain);
    }

    @Override
    public List<InformeEspejoSombra> deParticipante(UserId participanteId) {
        return repository.deParticipante(participanteId.value()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public InformeEspejoSombra save(InformeEspejoSombra informe) {
        var saved = repository.saveAndFlush(mapper.toEntity(informe));
        return mapper.toDomain(saved);
    }
}
