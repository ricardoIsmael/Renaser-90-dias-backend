package com.renaser.os.academy.infrastructure.adapter.out.persistence.asignacion;

import com.renaser.os.academy.application.ports.out.asignacion.LoadMiembroGrupoPort;
import com.renaser.os.academy.domain.model.asignacion.GrupoId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
class MiembroGrupoPersistenceAdapter implements LoadMiembroGrupoPort {

    private final SpringDataMiembroGrupoRepository repository;

    MiembroGrupoPersistenceAdapter(SpringDataMiembroGrupoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Set<UserId> usuariosDeGrupos(Set<GrupoId> grupoIds) {
        if (grupoIds.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = grupoIds.stream().map(GrupoId::value).toList();
        return repository.findByGrupoIdIn(ids).stream()
                .map(m -> UserId.of(m.getUsuarioId()))
                .collect(Collectors.toSet());
    }
}
