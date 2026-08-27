package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.estado;

import com.renaser.os.onboarding.application.ports.out.estado.LoadEstadoOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.estado.SaveEstadoOnboardingPort;
import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@code usuarioId} es la PK de la tabla (no un id autogenerado) — {@code save()} de Spring
 * Data JPA hace {@code merge()} para un id ya asignado, asi que es upsert de forma natural,
 * sin necesitar el truco de "buscar y reutilizar id" que usan Respuesta/GrabacionV90.
 */
@Component
class EstadoOnboardingPersistenceAdapter implements LoadEstadoOnboardingPort, SaveEstadoOnboardingPort {

    private final SpringDataEstadoOnboardingRepository repository;
    private final EstadoOnboardingPersistenceMapper mapper;

    EstadoOnboardingPersistenceAdapter(SpringDataEstadoOnboardingRepository repository,
                                        EstadoOnboardingPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<EstadoOnboarding> deUsuario(UserId usuarioId) {
        return repository.findById(usuarioId.value()).map(mapper::toDomain);
    }

    @Override
    public ResumenEstadosOnboarding contarResumen() {
        return new ResumenEstadosOnboarding(repository.count(), repository.countByCompletadoTrue(),
                repository.countByPactoFirmadoEnIsNotNull());
    }

    @Override
    public EstadoOnboarding guardar(EstadoOnboarding estado) {
        var saved = repository.saveAndFlush(mapper.toEntity(estado));
        return mapper.toDomain(saved);
    }
}
