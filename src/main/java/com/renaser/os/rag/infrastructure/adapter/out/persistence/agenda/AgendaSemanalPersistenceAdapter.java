package com.renaser.os.rag.infrastructure.adapter.out.persistence.agenda;

import com.renaser.os.rag.application.ports.out.agenda.AgendaSemanalPort;
import com.renaser.os.rag.domain.model.agenda.AgendaOcupada;
import com.renaser.os.rag.domain.model.agenda.AgendaOcupada.Tramo;
import com.renaser.os.rag.domain.model.agenda.AgendaSemanal;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code agenda_ocupada} (V64). Mapper a mano: {@code smallint} de minutos y dia ISO a
 * {@link Tramo} y {@link DayOfWeek}. Guardar reemplaza todas las filas de la persona en una sola
 * transaccion: nunca queda media agenda.
 */
@Component
class AgendaSemanalPersistenceAdapter implements AgendaSemanalPort {

    private final SpringDataAgendaOcupadaRepository repository;

    AgendaSemanalPersistenceAdapter(SpringDataAgendaOcupadaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public AgendaSemanal de(UserId participanteId) {
        Map<DayOfWeek, List<Tramo>> porDia = repository.findByParticipanteId(participanteId.value()).stream()
                .collect(Collectors.groupingBy(fila -> DayOfWeek.of(fila.getDiaSemana()),
                        () -> new EnumMap<>(DayOfWeek.class),
                        Collectors.mapping(fila -> new Tramo(fila.getDesdeMinuto(), fila.getHastaMinuto()),
                                Collectors.toList())));
        Map<DayOfWeek, AgendaOcupada> dias = new EnumMap<>(DayOfWeek.class);
        porDia.forEach((dia, tramos) -> dias.put(dia, new AgendaOcupada(tramos)));
        return new AgendaSemanal(dias);
    }

    @Override
    @Transactional
    public void guardar(UserId participanteId, AgendaSemanal agenda) {
        repository.borrarDe(participanteId.value());
        List<AgendaOcupadaJpaEntity> filas = new ArrayList<>();
        agenda.dias().forEach((dia, ocupada) -> ocupada.ocupados().forEach(tramo -> filas.add(
                new AgendaOcupadaJpaEntity(UUID.randomUUID(), participanteId.value(), (short) dia.getValue(),
                        (short) tramo.desde(), (short) tramo.hasta()))));
        repository.saveAll(filas);
    }
}
