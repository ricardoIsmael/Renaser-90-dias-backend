package com.renaser.os.community.infrastructure.adapter.out.persistence.cohorte;

import com.renaser.os.community.application.ports.out.cohorte.EliminarCohortePort;
import com.renaser.os.community.application.ports.out.cohorte.LoadCohortePort;
import com.renaser.os.community.application.ports.out.cohorte.SaveCohortePort;
import com.renaser.os.community.domain.model.cohorte.Cohorte;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.community.domain.model.cohorte.EstadoCohorte;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class CohortePersistenceAdapter implements LoadCohortePort, SaveCohortePort, EliminarCohortePort {

    private final SpringDataCohorteRepository repository;
    private final CohortePersistenceMapper mapper;
    private final EntityManager entityManager;

    CohortePersistenceAdapter(SpringDataCohorteRepository repository, CohortePersistenceMapper mapper,
                               EntityManager entityManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Cohorte> porId(CohorteId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<Cohorte> listar(EstadoCohorte filtroEstado) {
        List<CohorteJpaEntity> filas = filtroEstado == null
                ? repository.findAllByOrderByCreadoEnDesc()
                : repository.findByEstadoOrderByCreadoEnDesc(mapper.toJpaEstado(filtroEstado));
        return filas.stream().map(mapper::toDomain).toList();
    }

    @Override
    public int contarCelulas(CohorteId id) {
        Number total = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM renaser.celulas WHERE cohorte_id = ?1")
                .setParameter(1, id.value())
                .getSingleResult();
        return total.intValue();
    }

    @Override
    public Cohorte save(Cohorte cohorte) {
        var guardada = repository.saveAndFlush(mapper.toEntity(cohorte));
        return mapper.toDomain(guardada);
    }

    @Override
    public void eliminar(CohorteId id) {
        repository.deleteById(id.value());
    }
}
