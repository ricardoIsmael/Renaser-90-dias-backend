package com.renaser.os.habits.application.services;

import com.renaser.os.points.api.PorcentajeHabitosFinder;
import com.renaser.os.habits.application.ports.out.registro.ContarRegistrosDiariosHabitsPort;
import com.renaser.os.habits.domain.model.registro.ConteoDiarioHabitos;
import com.renaser.os.habits.domain.model.registro.PorcentajeHabitos;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * D-43 — implementa {@link PorcentajeHabitosFinder} con UNA sola consulta en
 * lote ({@link ContarRegistrosDiariosHabitsPort}) para todos los participantes
 * pedidos, y aplica la regla de negocio ({@link PorcentajeHabitos}) en Java,
 * no en SQL — testeable sin Postgres.
 */
@Service
public class PorcentajeHabitosService implements PorcentajeHabitosFinder {

    /** Ventana de Ley VI: 7 dias UTC cerrados incluyendo "hasta" (coherence-score/route.ts:56-63). */
    private static final long VENTANA_DIAS = 7L;

    private final ContarRegistrosDiariosHabitsPort contarPort;

    public PorcentajeHabitosService(ContarRegistrosDiariosHabitsPort contarPort) {
        this.contarPort = contarPort;
    }

    @Override
    public Map<UserId, BigDecimal> porcentajePorParticipante(Collection<UserId> participantes, LocalDate hasta) {
        if (participantes.isEmpty()) {
            return Map.of();
        }

        LocalDate desde = hasta.minusDays(VENTANA_DIAS - 1);
        Map<UserId, List<ConteoDiarioHabitos>> conteosPorParticipante =
                contarPort.contarPorParticipanteYDia(participantes, desde, hasta);

        Map<UserId, BigDecimal> resultado = new LinkedHashMap<>();
        for (UserId participanteId : participantes) {
            if (resultado.containsKey(participanteId)) {
                continue;
            }
            // Sin clave en el mapa = sin dato, y NO un cien (2026-09-16, mismo criterio que rocas
            // desde D-128): quien no tuvo un solo dia con habitos calificables en la ventana no
            // tiene porcentaje que aportar al ranking.
            PorcentajeHabitos.calcular(conteosPorParticipante.getOrDefault(participanteId, List.of()))
                    .ifPresent(porcentaje -> resultado.put(participanteId, porcentaje.valor()));
        }
        return resultado;
    }
}
