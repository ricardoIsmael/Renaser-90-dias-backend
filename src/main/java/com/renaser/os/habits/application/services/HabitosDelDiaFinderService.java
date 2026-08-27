package com.renaser.os.habits.application.services;

import com.renaser.os.points.api.HabitoDelDiaResumen;
import com.renaser.os.points.api.HabitosDelDiaFinder;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fachada liviana de {@link HabitosDelDiaFinder} sobre {@link ConsultarTracksDelDiaUseCase}
 * — misma consulta de tracks que ya usa {@code TracksDelDiaProyeccionService}, pero sin
 * resolver horario/guia/preferencia (esta vista no los necesita). El titulo del habito se
 * batchea con {@link LoadHabitoPort#porIds}, nunca N+1.
 */
@Service
class HabitosDelDiaFinderService implements HabitosDelDiaFinder {

    private final ConsultarTracksDelDiaUseCase consultarTracksUseCase;
    private final LoadHabitoPort loadHabitoPort;

    HabitosDelDiaFinderService(ConsultarTracksDelDiaUseCase consultarTracksUseCase, LoadHabitoPort loadHabitoPort) {
        this.consultarTracksUseCase = consultarTracksUseCase;
        this.loadHabitoPort = loadHabitoPort;
    }

    @Override
    public List<HabitoDelDiaResumen> deHoy(UserId participanteId, LocalDate fecha) {
        List<RegistroHabito> registros = consultarTracksUseCase.consultar(participanteId, participanteId, fecha);
        if (registros.isEmpty()) {
            return List.of();
        }

        Set<HabitoId> habitoIds = registros.stream().map(RegistroHabito::habitoId).collect(Collectors.toSet());
        Map<HabitoId, String> tituloPorHabito = loadHabitoPort.porIds(habitoIds).stream()
                .collect(Collectors.toMap(Habito::id, Habito::titulo));

        return registros.stream()
                .map(registro -> new HabitoDelDiaResumen(registro.id().value(),
                        tituloPorHabito.get(registro.habitoId()), registro.estado().name()))
                .toList();
    }
}
