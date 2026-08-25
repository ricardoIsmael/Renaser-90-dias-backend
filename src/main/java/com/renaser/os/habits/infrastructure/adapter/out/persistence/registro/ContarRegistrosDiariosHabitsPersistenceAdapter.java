package com.renaser.os.habits.infrastructure.adapter.out.persistence.registro;

import com.renaser.os.habits.application.ports.out.registro.ContarRegistrosDiariosHabitsPort;
import com.renaser.os.habits.domain.model.registro.ConteoDiarioHabitos;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * D-43 — UNA sola consulta ({@link SpringDataRegistroHabitoRepository#contarPorParticipanteYDiaEnRango})
 * para todos los participantes pedidos, sin importar cuantos sean. Una
 * implementacion que llamara al repositorio una vez por participante seria
 * exactamente el N+1 que la decision busca evitar.
 */
@Component
class ContarRegistrosDiariosHabitsPersistenceAdapter implements ContarRegistrosDiariosHabitsPort {

    private final SpringDataRegistroHabitoRepository repository;

    ContarRegistrosDiariosHabitsPersistenceAdapter(SpringDataRegistroHabitoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Map<UserId, List<ConteoDiarioHabitos>> contarPorParticipanteYDia(Collection<UserId> participantes,
                                                                              LocalDate desde, LocalDate hasta) {
        if (participantes.isEmpty()) {
            return Map.of();
        }

        List<UUID> ids = participantes.stream().map(UserId::value).distinct().toList();
        List<ConteoDiarioHabitosProjection> filas = repository.contarPorParticipanteYDiaEnRango(ids, desde, hasta,
                EstadoRegistroJpa.COMPLETADO);

        return filas.stream()
                .collect(Collectors.groupingBy(
                        fila -> UserId.of(fila.getParticipanteId()),
                        Collectors.mapping(this::toDomain, Collectors.toList())));
    }

    private ConteoDiarioHabitos toDomain(ConteoDiarioHabitosProjection fila) {
        return new ConteoDiarioHabitos(fila.getFecha(), (int) fila.getTotalRegistros(), (int) fila.getCompletados(),
                (int) fila.getOpcionalesNoCompletados());
    }
}
