package com.renaser.os.points.infrastructure.adapter.out.persistence.ajuste;

import com.renaser.os.points.application.ports.out.ajuste.SaveAjustePort;
import com.renaser.os.points.domain.model.ajuste.AjustePuntos;
import org.springframework.stereotype.Component;

@Component
class AjustePuntosPersistenceAdapter implements SaveAjustePort {

    private final SpringDataAjustePuntosRepository repository;
    private final AjustePuntosPersistenceMapper mapper;

    AjustePuntosPersistenceAdapter(SpringDataAjustePuntosRepository repository,
                                    AjustePuntosPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public AjustePuntos save(AjustePuntos ajuste) {
        var saved = repository.save(mapper.toEntity(ajuste));
        return mapper.toDomain(saved);
    }
}
