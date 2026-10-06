package com.renaser.os.calendar.infrastructure.adapter.out.persistence.confirmacion;

import com.renaser.os.calendar.application.ports.out.confirmacion.LoadConfirmacionPort;
import com.renaser.os.calendar.application.ports.out.confirmacion.SaveConfirmacionPort;
import com.renaser.os.calendar.domain.model.confirmacion.Confirmacion;
import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
class ConfirmacionPersistenceAdapter implements LoadConfirmacionPort, SaveConfirmacionPort {

    private final SpringDataConfirmacionRepository repository;

    ConfirmacionPersistenceAdapter(SpringDataConfirmacionRepository repository) {
        this.repository = repository;
    }

    @Override
    public Map<String, EstadoConfirmacion> paraVisor(UserId usuarioId, Set<EventoId> eventoIds) {
        if (eventoIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = eventoIds.stream().map(EventoId::value).toList();
        Map<String, EstadoConfirmacion> resultado = new HashMap<>();
        for (var fila : repository.findByUsuarioIdAndEventoIdIn(usuarioId.value(), ids)) {
            resultado.put(fila.getEventoId() + "|" + fila.getInicioOcurrencia(), toDomain(fila.getEstado()));
        }
        return resultado;
    }

    @Override
    public Set<String> confirmadosAsistencia(EventoId eventoId, List<Instant> ocurrencias) {
        if (ocurrencias.isEmpty()) {
            return Set.of();
        }
        Set<String> resultado = new java.util.HashSet<>();
        for (var fila : repository.findByEventoIdAndInicioOcurrenciaInAndEstado(eventoId.value(), ocurrencias,
                EstadoConfirmacionJpa.ASISTE)) {
            resultado.add(fila.getInicioOcurrencia() + "|" + fila.getUsuarioId());
        }
        return resultado;
    }

    @Override
    public Optional<EstadoConfirmacion> estadoDe(EventoId eventoId, Instant inicioOcurrencia, UserId usuarioId) {
        return repository.findById(new ConfirmacionEventoId(eventoId.value(), inicioOcurrencia, usuarioId.value()))
                .map(fila -> toDomain(fila.getEstado()));
    }

    @Override
    public List<Confirmacion> deOcurrencia(EventoId eventoId, Instant inicioOcurrencia) {
        return repository.findByEventoIdAndInicioOcurrencia(eventoId.value(), inicioOcurrencia).stream()
                .map(fila -> new Confirmacion(EventoId.of(fila.getEventoId()), fila.getInicioOcurrencia(),
                        UserId.of(fila.getUsuarioId()), toDomain(fila.getEstado()), fila.getCreadoEn(),
                        fila.getActualizadoEn()))
                .toList();
    }

    @Override
    public void upsert(Confirmacion confirmacion) {
        var existente = repository.findById(new ConfirmacionEventoId(confirmacion.eventoId().value(),
                confirmacion.inicioOcurrencia(), confirmacion.usuarioId().value()));
        Instant creadoEn = existente.map(ConfirmacionEventoJpaEntity::getCreadoEn).orElse(confirmacion.creadoEn());
        // saveAndFlush, no save: ConfirmacionService.confirmar() llama despues, en la misma
        // transaccion, a saveRecordatorioPort.cancelarPorAsistencia() — un DELETE
        // @Modifying(clearAutomatically=true) que limpia el contexto y descarta este save si
        // no esta flusheado (encontrado probando el endpoint: RSVP "GOING" respondia 200 sin
        // persistir nada; "NOT_GOING"/"MAYBE" andaban porque no disparan esa llamada).
        // Desde D-189 esa llamada ya no existe; el flush se deja porque no cuesta nada y evita que
        // el bug vuelva si algun dia otra operacion @Modifying corre despues en la misma transaccion.
        repository.saveAndFlush(new ConfirmacionEventoJpaEntity(confirmacion.eventoId().value(),
                confirmacion.inicioOcurrencia(), confirmacion.usuarioId().value(), toJpa(confirmacion.estado()),
                creadoEn, confirmacion.actualizadoEn()));
    }

    private static EstadoConfirmacion toDomain(EstadoConfirmacionJpa jpa) {
        return EstadoConfirmacion.valueOf(jpa.name());
    }

    private static EstadoConfirmacionJpa toJpa(EstadoConfirmacion dominio) {
        return EstadoConfirmacionJpa.valueOf(dominio.name());
    }
}
