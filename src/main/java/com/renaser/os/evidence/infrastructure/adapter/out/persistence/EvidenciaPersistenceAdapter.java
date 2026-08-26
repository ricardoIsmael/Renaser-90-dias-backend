package com.renaser.os.evidence.infrastructure.adapter.out.persistence;

import com.renaser.os.evidence.application.ports.out.evidencia.LoadEvidenciaPort;
import com.renaser.os.evidence.application.ports.out.evidencia.SaveEvidenciaPort;
import com.renaser.os.evidence.domain.model.evidencia.Evidencia;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
class EvidenciaPersistenceAdapter implements LoadEvidenciaPort, SaveEvidenciaPort {

    private final SpringDataEvidenciaRepository repository;
    private final EvidenciaPersistenceMapper mapper;

    EvidenciaPersistenceAdapter(SpringDataEvidenciaRepository repository, EvidenciaPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Evidencia> byId(EvidenciaId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<Evidencia> pendientesLote(Instant hasta, int limite) {
        return repository.pendientesLote(EstadoValidacionJpa.PENDIENTE, hasta, PageRequest.of(0, limite)).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Evidencia save(Evidencia evidencia) {
        var saved = repository.saveAndFlush(mapper.toEntity(evidencia));
        return mapper.toDomain(saved);
    }

    /**
     * {@code limite + 1} filas (mismo truco que {@code community.PublicacionPersistenceAdapter.paginaDe}):
     * el llamador sabe si hay página siguiente sin un COUNT aparte.
     */
    @Override
    public List<Evidencia> buscar(FiltroEvidencia filtro, Instant cursor, int limite) {
        Specification<EvidenciaJpaEntity> spec = EvidenciaSpecifications.filtro(filtro, cursor);
        Pageable pageable = PageRequest.of(0, limite + 1, Sort.by(Sort.Direction.DESC, "creadoEn"));
        return repository.findAll(spec, pageable).getContent().stream().map(mapper::toDomain).toList();
    }
}
