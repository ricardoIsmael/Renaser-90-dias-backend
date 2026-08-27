package com.renaser.os.habits.infrastructure.adapter.out.persistence.radar;

import com.renaser.os.users.api.RadarLogrosFinder;
import com.renaser.os.habits.application.ports.out.radar.LoadRegistroRadarPort;
import com.renaser.os.habits.application.ports.out.radar.SaveRegistroRadarPort;
import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.shared.domain.UserId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Implementa ademas {@link RadarLogrosFinder}, el contrato PUBLICO hacia otros modulos —
 * mismo patron que {@code EntradaDiarioPersistenceAdapter}. */
@Component
class RegistroRadarPersistenceAdapter implements LoadRegistroRadarPort, SaveRegistroRadarPort, RadarLogrosFinder {

    private final SpringDataRegistroRadarRepository repository;
    private final RegistroRadarPersistenceMapper mapper;

    RegistroRadarPersistenceAdapter(SpringDataRegistroRadarRepository repository,
                                     RegistroRadarPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<RegistroRadar> ultimoDeParticipante(UserId participanteId) {
        return repository.findTopByParticipanteIdOrderByCreadoEnDesc(participanteId.value()).map(mapper::toDomain);
    }

    @Override
    public List<RegistroRadar> historialDeParticipante(UserId participanteId, Instant cursor, int tamanoPagina) {
        Pageable pageable = PageRequest.of(0, tamanoPagina);
        List<RegistroRadarJpaEntity> entidades = cursor != null
                ? repository.findByParticipanteIdAndCreadoEnLessThanOrderByCreadoEnDesc(participanteId.value(),
                        cursor, pageable)
                : repository.findByParticipanteIdOrderByCreadoEnDesc(participanteId.value(), pageable);
        return entidades.stream().map(mapper::toDomain).toList();
    }

    @Override
    public RegistroRadar save(RegistroRadar registro) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(registro)));
    }

    @Override
    public long totalRegistrosRadar(UserId participanteId) {
        return repository.countByParticipanteId(participanteId.value());
    }

    @Override
    public Optional<Instant> primerRegistroRadarEn(UserId participanteId) {
        return Optional.ofNullable(repository.minCreadoEnPorParticipante(participanteId.value()));
    }
}
