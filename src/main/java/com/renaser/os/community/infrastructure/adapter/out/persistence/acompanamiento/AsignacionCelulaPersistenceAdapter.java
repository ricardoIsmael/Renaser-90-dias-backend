package com.renaser.os.community.infrastructure.adapter.out.persistence.acompanamiento;

import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.acompanamiento.SaveAsignacionPort;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class AsignacionCelulaPersistenceAdapter implements LoadAsignacionesPort, SaveAsignacionPort {

    private final SpringDataAsignacionCelulaRepository repository;
    private final AsignacionCelulaPersistenceMapper mapper;

    AsignacionCelulaPersistenceAdapter(SpringDataAsignacionCelulaRepository repository,
                                        AsignacionCelulaPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<AsignacionCelula> porUsuario(UserId usuarioId) {
        return repository.findByUsuarioIdOrderByInicioDesc(usuarioId.value()).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<AsignacionCelula> porCelula(CelulaId celulaId) {
        return repository.findByCelulaIdOrderByInicioDesc(celulaId.value()).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<AsignacionCelula> porClaveOperacion(String claveOperacion) {
        return repository.findFirstByClaveOperacion(claveOperacion).map(mapper::toDomain);
    }

    @Override
    public AsignacionCelula save(AsignacionCelula asignacion) {
        // saveAndFlush: las restricciones EXCLUDE de V45 tienen que fallar aca dentro de la
        // transaccion del caso de uso, no al cerrar la sesion de Hibernate mas tarde.
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(asignacion)));
    }
}
