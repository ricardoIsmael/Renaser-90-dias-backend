package com.renaser.os.habits.infrastructure.adapter.out.persistence.habito;

import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.SaveHabitoPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
class HabitoPersistenceAdapter implements LoadHabitoPort, SaveHabitoPort {

    private final SpringDataHabitoRepository repository;
    private final HabitoPersistenceMapper mapper;

    HabitoPersistenceAdapter(SpringDataHabitoRepository repository, HabitoPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Habito> byId(HabitoId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<Habito> porIds(Collection<HabitoId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<java.util.UUID> valores = ids.stream().map(HabitoId::value).toList();
        return repository.findByIdIn(valores).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Habito> catalogoActivo() {
        return repository.findByAmbitoAndActivoTrue(AmbitoHabitoJpa.SISTEMA).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Habito> catalogoCompleto() {
        return repository.findByAmbito(AmbitoHabitoJpa.SISTEMA).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Habito> personalesActivosDe(UserId participanteId) {
        return repository.findByAmbitoAndParticipanteIdAndActivoTrue(AmbitoHabitoJpa.PERSONAL, participanteId.value())
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Habito> porClaveSistema(String claveSistema) {
        return repository.findByClaveSistema(claveSistema).map(mapper::toDomain);
    }

    @Override
    public Habito save(Habito habito) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(habito)));
    }

    @Override
    public void eliminar(HabitoId id) {
        // flush() fuerza el DELETE ya, para que un violacion de la FK RESTRICT de
        // registros_habito (SaveHabitoPort.eliminar) explote aca dentro del mismo metodo
        // transaccional, no en un flush tardio en otro punto del request.
        repository.deleteById(id.value());
        repository.flush();
    }
}
