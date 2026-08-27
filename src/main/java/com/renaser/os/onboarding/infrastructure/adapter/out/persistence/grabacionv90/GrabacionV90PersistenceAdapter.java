package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.grabacionv90;

import com.renaser.os.onboarding.application.ports.out.grabacionv90.LoadGrabacionV90Port;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.SaveGrabacionV90Port;
import com.renaser.os.onboarding.domain.model.grabacionv90.EstadoIAv90;
import com.renaser.os.onboarding.domain.model.grabacionv90.GrabacionV90;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class GrabacionV90PersistenceAdapter implements LoadGrabacionV90Port, SaveGrabacionV90Port {

    private final SpringDataGrabacionV90Repository repository;
    private final GrabacionV90PersistenceMapper mapper;

    GrabacionV90PersistenceAdapter(SpringDataGrabacionV90Repository repository,
                                    GrabacionV90PersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<GrabacionV90> porId(long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<GrabacionV90> porSlot(UserId usuarioId, String fase, String eje, short indice) {
        return repository.findByUsuarioIdAndFaseAndEjeAndIndice(usuarioId.value(), fase, eje, indice)
                .map(mapper::toDomain);
    }

    @Override
    public List<GrabacionV90> todasDeUsuario(UserId usuarioId) {
        return repository.findByUsuarioId(usuarioId.value()).stream().map(mapper::toDomain).toList();
    }

    /** Nombres identicos dominio<->JPA a proposito (ver EstadoIAv90Jpa) — {@code valueOf} directo, sin mapper. */
    @Override
    public long contarPorEstado(EstadoIAv90 estado) {
        return repository.countByEstadoIa(EstadoIAv90Jpa.valueOf(estado.name()));
    }

    /**
     * UPSERT por {@code (usuarioId, fase, eje, indice)}: mismo criterio que
     * {@code RespuestaPersistenceAdapter.guardar} — reutiliza el id existente si ya hay
     * fila para ese slot.
     */
    @Override
    public GrabacionV90 guardar(GrabacionV90 grabacion) {
        GrabacionV90JpaEntity entity = mapper.toEntity(grabacion);
        if (entity.getId() == null) {
            repository.findByUsuarioIdAndFaseAndEjeAndIndice(grabacion.usuarioId().value(), grabacion.fase(),
                            grabacion.eje(), grabacion.indice())
                    .ifPresent(existente -> entity.setId(existente.getId()));
        }
        var saved = repository.saveAndFlush(entity);
        return mapper.toDomain(saved);
    }
}
