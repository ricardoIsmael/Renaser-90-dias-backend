package com.renaser.os.users.infrastructure.adapter.out.persistence.emergencia;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.emergencia.LoadSolicitudDeEmergenciaPort;
import com.renaser.os.users.application.ports.out.emergencia.SaveSolicitudDeEmergenciaPort;
import com.renaser.os.users.domain.model.emergencia.EstadoDeEmergencia;
import com.renaser.os.users.domain.model.emergencia.SolicitudDeEmergencia;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
class SolicitudDeEmergenciaPersistenceAdapter implements LoadSolicitudDeEmergenciaPort, SaveSolicitudDeEmergenciaPort {

    private final SpringDataSolicitudDeEmergenciaRepository repository;
    private final SolicitudDeEmergenciaPersistenceMapper mapper;

    SolicitudDeEmergenciaPersistenceAdapter(SpringDataSolicitudDeEmergenciaRepository repository,
                                            SolicitudDeEmergenciaPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<SolicitudDeEmergencia> abiertaDe(UserId aprendizId) {
        return repository.findFirstByAprendizIdAndEstado(aprendizId.value(), EstadoDeEmergencia.ABIERTA.name())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<SolicitudDeEmergencia> porId(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    /** {@code saveAndFlush}: el índice único parcial salta acá (409) y no al commit, lejos de quien lo causó. */
    @Override
    public SolicitudDeEmergencia save(SolicitudDeEmergencia solicitud) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(solicitud)));
    }
}
