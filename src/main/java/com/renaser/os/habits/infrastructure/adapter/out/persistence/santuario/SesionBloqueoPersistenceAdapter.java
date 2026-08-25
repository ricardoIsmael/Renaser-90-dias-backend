package com.renaser.os.habits.infrastructure.adapter.out.persistence.santuario;

import com.renaser.os.habits.application.ports.out.santuario.LoadSesionBloqueoPort;
import com.renaser.os.habits.application.ports.out.santuario.SaveSesionBloqueoPort;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.habits.domain.model.santuario.SesionBloqueo;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class SesionBloqueoPersistenceAdapter implements LoadSesionBloqueoPort, SaveSesionBloqueoPort {

    private final SpringDataSesionBloqueoRepository repository;
    private final SesionBloqueoPersistenceMapper mapper;

    SesionBloqueoPersistenceAdapter(SpringDataSesionBloqueoRepository repository,
                                     SesionBloqueoPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<SesionBloqueo> porRegistro(RegistroHabitoId registroHabitoId) {
        return repository.findById(registroHabitoId.value()).map(mapper::toDomain);
    }

    @Override
    public SesionBloqueo save(SesionBloqueo sesion) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(sesion)));
    }
}
