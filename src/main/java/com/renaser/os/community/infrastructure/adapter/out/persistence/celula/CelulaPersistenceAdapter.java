package com.renaser.os.community.infrastructure.adapter.out.persistence.celula;

import com.renaser.os.community.application.ports.out.celula.EliminarCelulaPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.application.ports.out.celula.SaveCelulaPort;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class CelulaPersistenceAdapter implements LoadCelulaPort, SaveCelulaPort, EliminarCelulaPort {

    private final SpringDataCelulaRepository repository;
    private final CelulaPersistenceMapper mapper;

    CelulaPersistenceAdapter(SpringDataCelulaRepository repository, CelulaPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Celula> porId(CelulaId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<Celula> porCohorte(CohorteId cohorteId) {
        return repository.findByCohorteIdOrderByNombreAsc(cohorteId.value()).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Celula> todas() {
        return repository.findAllByOrderByNombreAsc().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Celula> porMentor(UserId mentorId) {
        return repository.findByMentorId(mentorId.value()).map(mapper::toDomain);
    }

    @Override
    public Celula save(Celula celula) {
        var guardada = repository.saveAndFlush(mapper.toEntity(celula));
        return mapper.toDomain(guardada);
    }

    @Override
    public void eliminar(CelulaId id) {
        repository.deleteById(id.value());
    }
}
