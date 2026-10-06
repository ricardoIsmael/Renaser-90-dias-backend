package com.renaser.os.habits.infrastructure.adapter.out.persistence.registro;

import com.renaser.os.users.api.HabitoLogrosFinder;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.registro.RetirarObligacionesPausadasPort;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Implementa ademas {@link HabitoLogrosFinder}, el contrato PUBLICO hacia otros modulos
 * — mismo patron que {@code EntradaDiarioPersistenceAdapter} de este mismo modulo. */
@Component
class RegistroHabitoPersistenceAdapter implements LoadRegistroHabitoPort, SaveRegistroHabitoPort, HabitoLogrosFinder, RetirarObligacionesPausadasPort {

    private final SpringDataRegistroHabitoRepository repository;
    private final RegistroHabitoPersistenceMapper mapper;

    RegistroHabitoPersistenceAdapter(SpringDataRegistroHabitoRepository repository,
                                      RegistroHabitoPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<RegistroHabito> byIdParaEscritura(RegistroHabitoId id) {
        return repository.findByIdParaEscritura(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<RegistroHabito> byId(RegistroHabitoId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<RegistroHabito> porParticipanteHabitoYFecha(UserId participanteId, HabitoId habitoId,
                                                                  LocalDate fecha) {
        return repository.findByParticipanteIdAndHabitoIdAndFechaEjecucion(participanteId.value(), habitoId.value(),
                fecha).map(mapper::toDomain);
    }

    @Override
    public Optional<RegistroHabito> porParticipanteHabitoYFechaParaEscritura(UserId participanteId, HabitoId habitoId,
                                                                               LocalDate fecha) {
        return repository.findByParticipanteHabitoYFechaParaEscritura(participanteId.value(), habitoId.value(), fecha)
                .map(mapper::toDomain);
    }

    @Override
    public List<RegistroHabito> porParticipanteYFecha(UserId participanteId, LocalDate fecha) {
        return repository.findByParticipanteIdAndFechaEjecucion(participanteId.value(), fecha).stream()
                .map(mapper::toDomain).toList();
    }

    @Override
    public Map<HabitoId, Integer> diaProgramaMasAltoGeneradoPorHabito(UserId participanteId,
                                                                      Collection<HabitoId> habitos) {
        if (habitos.isEmpty()) {
            return Map.of();
        }
        Map<HabitoId, Integer> resultado = new HashMap<>();
        repository.diaProgramaMasAltoPorHabito(participanteId.value(),
                        habitos.stream().map(HabitoId::value).distinct().toList())
                .forEach(fila -> resultado.put(HabitoId.of((UUID) fila[0]), ((Number) fila[1]).intValue()));
        return resultado;
    }

    @Override
    public List<RegistroHabito> pendientesDeParticipanteAnterioresA(UserId participanteId, LocalDate fecha) {
        return repository.findByParticipanteIdAndEstadoAndFechaEjecucionLessThan(participanteId.value(),
                EstadoRegistroJpa.PENDIENTE, fecha).stream().map(mapper::toDomain).toList();
    }

    @Override
    public RegistroHabito save(RegistroHabito registro) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(registro)));
    }

    @Override
    public boolean insertarSiNoExiste(RegistroHabito registro) {
        return repository.insertarSiNoExiste(mapper.toEntity(registro)) == 1;
    }

    @Override
    public long totalHabitosCompletados(UserId participanteId) {
        return repository.countByParticipanteIdAndEstado(participanteId.value(), EstadoRegistroJpa.COMPLETADO);
    }

    @Override
    public Optional<Instant> primerHabitoCompletadoEn(UserId participanteId) {
        return Optional.ofNullable(
                repository.minCompletadoEnPorParticipanteYEstado(participanteId.value(), EstadoRegistroJpa.COMPLETADO));
    }

    @Override
    @Transactional
    public int retirarPendientes(UserId participanteId, HabitoId habitoId, LocalDate desde, LocalDate hasta) {
        return repository.borrarPendientesEnRango(participanteId.value(), habitoId.value(), desde, hasta);
    }
}
