package com.renaser.os.calendar.infrastructure.adapter.out.persistence.evento;

import com.renaser.os.calendar.application.ports.out.evento.LoadExcepcionPort;
import com.renaser.os.calendar.application.ports.out.evento.SaveExcepcionPort;
import com.renaser.os.calendar.domain.model.evento.Excepcion;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.Clock;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
class ExcepcionPersistenceAdapter implements LoadExcepcionPort, SaveExcepcionPort {

    private final SpringDataExcepcionRepository repository;
    private final Clock clock;

    ExcepcionPersistenceAdapter(SpringDataExcepcionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public List<Excepcion> porEvento(EventoId eventoId) {
        return repository.findByEventoId(eventoId.value()).stream().map(ExcepcionPersistenceAdapter::toDomain).toList();
    }

    @Override
    public Map<EventoId, List<Excepcion>> porEventos(Set<EventoId> eventoIds) {
        if (eventoIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = eventoIds.stream().map(EventoId::value).toList();
        return repository.findByEventoIdIn(ids).stream()
                .map(ExcepcionPersistenceAdapter::toDomain)
                .collect(Collectors.groupingBy(Excepcion::eventoId));
    }

    @Override
    public Excepcion upsert(Excepcion excepcion) {
        var existente = repository.findByEventoIdAndInicioOcurrencia(excepcion.eventoId().value(),
                excepcion.inicioOcurrencia());
        UUID id = existente.map(ExcepcionEventoJpaEntity::getId).orElse(excepcion.id());
        var entidad = new ExcepcionEventoJpaEntity(id, excepcion.eventoId().value(), excepcion.inicioOcurrencia(),
                excepcion.cancelada(), excepcion.nuevoInicio(), excepcion.nuevaDuracion(), excepcion.nuevoTitulo(),
                existente.map(ExcepcionEventoJpaEntity::getCreadoEn).orElseGet(clock::now));
        // saveAndFlush, no save: EventoService.cancelar() llama despues, en la misma
        // transaccion, a saveRecordatorioPort.cancelarPorOcurrencia() — un DELETE
        // @Modifying(clearAutomatically=true) que limpia el contexto y descarta este save si
        // no esta flusheado (encontrado probando el endpoint: cancelar una ocurrencia
        // respondia 200 sin persistir nada, la ocurrencia seguia viva para todos).
        var guardada = repository.saveAndFlush(entidad);
        return toDomain(guardada);
    }

    private static Excepcion toDomain(ExcepcionEventoJpaEntity e) {
        return new Excepcion(e.getId(), EventoId.of(e.getEventoId()), e.getInicioOcurrencia(), e.isCancelada(),
                e.getNuevoInicio(), e.getNuevaDuracion(), e.getNuevoTitulo());
    }
}
