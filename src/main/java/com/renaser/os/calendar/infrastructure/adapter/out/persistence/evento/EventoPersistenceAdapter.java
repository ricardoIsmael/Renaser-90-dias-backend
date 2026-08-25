package com.renaser.os.calendar.infrastructure.adapter.out.persistence.evento;

import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.SaveEventoPort;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
class EventoPersistenceAdapter implements LoadEventoPort, SaveEventoPort {

    private final SpringDataEventoRepository eventoRepo;
    private final SpringDataRecurrenciaRepository recurrenciaRepo;
    private final SpringDataDiaSemanaRepository diaSemanaRepo;
    private final SpringDataRolDestinoRepository rolDestinoRepo;
    private final SpringDataReglaRecordatorioRepository reglaRepo;
    private final EventoPersistenceMapper mapper;

    EventoPersistenceAdapter(SpringDataEventoRepository eventoRepo, SpringDataRecurrenciaRepository recurrenciaRepo,
                              SpringDataDiaSemanaRepository diaSemanaRepo,
                              SpringDataRolDestinoRepository rolDestinoRepo,
                              SpringDataReglaRecordatorioRepository reglaRepo, EventoPersistenceMapper mapper) {
        this.eventoRepo = eventoRepo;
        this.recurrenciaRepo = recurrenciaRepo;
        this.diaSemanaRepo = diaSemanaRepo;
        this.rolDestinoRepo = rolDestinoRepo;
        this.reglaRepo = reglaRepo;
        this.mapper = mapper;
    }

    @Override
    public Optional<Evento> byId(EventoId id) {
        return eventoRepo.findById(id.value()).map(e -> ensamblar(List.of(e)).get(0));
    }

    @Override
    public List<Evento> candidatosParaVisor(Instant desde, Instant hasta) {
        return ensamblar(eventoRepo.candidatosParaVisor(desde, hasta));
    }

    @Override
    public List<Evento> candidatosParaRecordatorios(Instant ahora, Instant hastaMax, Instant desdeAnuncio) {
        return ensamblar(eventoRepo.candidatosParaRecordatorios(ahora, hastaMax, desdeAnuncio));
    }

    /** Ensambla el agregado completo en lote: 4 consultas EXTRA totales (no 4*N) sin
     * importar cuantos eventos haya en la lista — recurrencia/dias_semana/roles_destino/
     * reglas_recordatorio se traen por IN y se agrupan en memoria. */
    private List<Evento> ensamblar(List<EventoJpaEntity> entidades) {
        if (entidades.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = entidades.stream().map(EventoJpaEntity::getId).toList();

        Map<UUID, RecurrenciaJpaEntity> recurrencias = recurrenciaRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(RecurrenciaJpaEntity::getEventoId, r -> r));
        Map<UUID, List<DiaSemanaRecurrenciaJpaEntity>> diasSemana = diaSemanaRepo.findByEventoIdIn(ids).stream()
                .collect(Collectors.groupingBy(DiaSemanaRecurrenciaJpaEntity::getEventoId));
        Map<UUID, List<RolDestinoEventoJpaEntity>> rolesDestino = rolDestinoRepo.findByEventoIdIn(ids).stream()
                .collect(Collectors.groupingBy(RolDestinoEventoJpaEntity::getEventoId));
        Map<UUID, List<ReglaRecordatorioEventoJpaEntity>> reglas = reglaRepo.findByEventoIdIn(ids).stream()
                .collect(Collectors.groupingBy(ReglaRecordatorioEventoJpaEntity::getEventoId));

        return entidades.stream()
                .map(e -> mapper.toDomain(e, recurrencias.get(e.getId()), diasSemana.getOrDefault(e.getId(), List.of()),
                        rolesDestino.getOrDefault(e.getId(), List.of()), reglas.getOrDefault(e.getId(), List.of())))
                .toList();
    }

    @Override
    public Evento guardar(Evento evento) {
        eventoRepo.saveAndFlush(mapper.toEntity(evento));

        // recurrencias_evento CASCADEa dias_semana_recurrencia en la BD — borrar la
        // recurrencia ya limpia sus dias; solo hace falta reinsertar en el orden correcto
        // (padre antes que hijo, por la FK dias_semana_recurrencia -> recurrencias_evento).
        recurrenciaRepo.deleteByEventoId(evento.id().value());
        if (evento.recurrencia() != null) {
            recurrenciaRepo.saveAndFlush(mapper.toEntityRecurrencia(evento.id(), evento.recurrencia()));
            if (!evento.recurrencia().diasSemana().isEmpty()) {
                // saveAllAndFlush, no saveAll: el proximo deleteByEventoId (otra tabla) es
                // @Modifying(clearAutomatically=true) y con IDs manuales (merge, no persist)
                // un saveAll sin flush se pierde en silencio al limpiar el contexto —
                // guardaba 200/201 sin persistir nada (encontrado probando los endpoints).
                diaSemanaRepo.saveAllAndFlush(mapper.toEntityDiasSemana(evento.id(), evento.recurrencia().diasSemana()));
            }
        }

        rolDestinoRepo.deleteByEventoId(evento.id().value());
        if (!evento.rolesDestino().isEmpty()) {
            rolDestinoRepo.saveAllAndFlush(mapper.toEntityRolesDestino(evento.id(), evento.rolesDestino()));
        }

        reglaRepo.deleteByEventoId(evento.id().value());
        if (!evento.reglasRecordatorio().isEmpty()) {
            reglaRepo.saveAllAndFlush(mapper.toEntityReglas(evento.id(), evento.reglasRecordatorio()));
        }

        return evento;
    }

    @Override
    public void eliminar(EventoId id) {
        // FK ON DELETE CASCADE (baseline) se encarga de recurrencias_evento (y con ella
        // dias_semana_recurrencia), roles_destino_evento, reglas_recordatorio_evento,
        // excepciones_evento, confirmaciones_evento y recordatorios_evento.
        eventoRepo.deleteById(id.value());
    }
}
