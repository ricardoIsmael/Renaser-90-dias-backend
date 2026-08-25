package com.renaser.os.academy.infrastructure.adapter.out.persistence.recomendacion;

import com.renaser.os.academy.application.ports.out.recomendacion.LoadRecomendacionPort;
import com.renaser.os.academy.application.ports.out.recomendacion.SaveRecomendacionPort;
import com.renaser.os.academy.domain.model.recomendacion.RecomendacionAcademia;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@Component
class RecomendacionAcademiaPersistenceAdapter implements LoadRecomendacionPort, SaveRecomendacionPort {

    private final SpringDataRecomendacionAcademiaRepository repository;
    private final RecomendacionAcademiaPersistenceMapper mapper;

    RecomendacionAcademiaPersistenceAdapter(SpringDataRecomendacionAcademiaRepository repository,
                                             RecomendacionAcademiaPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<RecomendacionAcademia> delDia(UserId participanteId, LocalDate fecha) {
        return repository.findById(new RecomendacionAcademiaId(participanteId.value(), fecha)).map(mapper::toDomain);
    }

    @Override
    public RecomendacionAcademia guardar(RecomendacionAcademia recomendacion) {
        var guardada = repository.save(mapper.toEntity(recomendacion));
        return mapper.toDomain(guardada);
    }
}
