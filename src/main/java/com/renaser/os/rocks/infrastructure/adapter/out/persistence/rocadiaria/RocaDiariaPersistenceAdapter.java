package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocadiaria;

import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.application.ports.out.rocadiaria.SaveRocaDiariaPort;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
class RocaDiariaPersistenceAdapter implements LoadRocaDiariaPort, SaveRocaDiariaPort {

    private final SpringDataRocaDiariaRepository repository;
    private final RocaDiariaPersistenceMapper mapper;

    RocaDiariaPersistenceAdapter(SpringDataRocaDiariaRepository repository, RocaDiariaPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<RocaDiaria> byId(RocaDiariaId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<RocaDiaria> byIdParaEscritura(RocaDiariaId id) {
        return repository.findByIdParaEscritura(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<RocaDiaria> deParticipanteYFecha(UserId participanteId, LocalDate fecha) {
        return repository.findByParticipanteIdAndFecha(participanteId.value(), fecha).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public int contarDeParticipanteYFecha(UserId participanteId, LocalDate fecha) {
        return repository.countByParticipanteIdAndFecha(participanteId.value(), fecha);
    }

    @Override
    public int contarCompletadasDeParticipante(UserId participanteId) {
        return repository.countByParticipanteIdAndCompletadaTrue(participanteId.value());
    }

    @Override
    public Optional<Instant> primeraCompletadaEnDeParticipante(UserId participanteId) {
        return repository.primeraCompletadaEnDeParticipante(participanteId.value());
    }

    @Override
    public List<LocalDate> fechasCompletadasDeParticipante(UserId participanteId) {
        return repository.fechasCompletadasDeParticipante(participanteId.value());
    }

    @Override
    public RocaDiaria save(RocaDiaria rocaDiaria) {
        var saved = repository.saveAndFlush(mapper.toEntity(rocaDiaria));
        return mapper.toDomain(saved);
    }

    @Override
    public List<RocaDiaria> saveAll(List<RocaDiaria> rocasDiarias) {
        var entidades = rocasDiarias.stream().map(mapper::toEntity).toList();
        return repository.saveAllAndFlush(entidades).stream().map(mapper::toDomain).toList();
    }
}
