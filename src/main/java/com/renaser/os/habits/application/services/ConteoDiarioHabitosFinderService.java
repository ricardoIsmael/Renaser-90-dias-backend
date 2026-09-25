package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.out.registro.ContarRegistrosDiariosHabitsPort;
import com.renaser.os.habits.domain.model.registro.ConteoDiarioHabitos;
import com.renaser.os.points.api.ConteoDelDia;
import com.renaser.os.points.api.ConteoDiarioHabitosFinder;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * D-168 — implementa {@link ConteoDiarioHabitosFinder} para el semáforo del aprendiz con la MISMA
 * consulta en lote que ya usa el ranking ({@link ContarRegistrosDiariosHabitsPort}, D-43): una
 * sola consulta para todos los participantes pedidos. No hay SQL nuevo.
 *
 * <p>Qué cuenta ese día lo decide {@link ConteoDiarioHabitos#calificables()} (los opcionales no
 * cumplidos quedan afuera), que es la regla que ya aplicaba el ranking: el semáforo y el ranking no
 * pueden tener dos definiciones de "hábito que contaba".
 */
@Service
public class ConteoDiarioHabitosFinderService implements ConteoDiarioHabitosFinder {

    private final ContarRegistrosDiariosHabitsPort contarPort;

    public ConteoDiarioHabitosFinderService(ContarRegistrosDiariosHabitsPort contarPort) {
        this.contarPort = contarPort;
    }

    @Override
    public Map<UserId, List<ConteoDelDia>> porParticipanteEntre(Collection<UserId> participantes, LocalDate desde,
                                                                LocalDate hasta) {
        if (participantes.isEmpty()) {
            return Map.of();
        }
        Map<UserId, List<ConteoDelDia>> resultado = new LinkedHashMap<>();
        contarPort.contarPorParticipanteYDia(participantes, desde, hasta).forEach((participanteId, dias) -> {
            if (!dias.isEmpty()) {
                resultado.put(participanteId, dias.stream().map(ConteoDiarioHabitosFinderService::aConteo).toList());
            }
        });
        return resultado;
    }

    private static ConteoDelDia aConteo(ConteoDiarioHabitos dia) {
        return new ConteoDelDia(dia.fecha(), dia.calificables(), dia.completados());
    }
}
