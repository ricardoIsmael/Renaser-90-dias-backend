package com.renaser.os.rocks.application.services;

import com.renaser.os.points.api.ConteoDelDia;
import com.renaser.os.points.api.ConteoDiarioObjetivosFinder;
import com.renaser.os.rocks.application.ports.out.coherencia.CargarConteoDiarioRocasPort;
import com.renaser.os.rocks.domain.model.coherencia.DiaRocas;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * D-168 — implementa {@link ConteoDiarioObjetivosFinder} para el semáforo del aprendiz con la MISMA
 * consulta en lote que ya alimenta la coherencia ({@link CargarConteoDiarioRocasPort}, D-43). Un
 * objetivo del día nunca es opcional: todo lo planificado cuenta, igual que en la coherencia.
 */
@Service
public class ConteoDiarioObjetivosService implements ConteoDiarioObjetivosFinder {

    private final CargarConteoDiarioRocasPort conteoPort;

    public ConteoDiarioObjetivosService(CargarConteoDiarioRocasPort conteoPort) {
        this.conteoPort = conteoPort;
    }

    @Override
    public Map<UserId, List<ConteoDelDia>> porParticipanteEntre(Collection<UserId> participantes, LocalDate desde,
                                                                LocalDate hasta) {
        if (participantes.isEmpty()) {
            return Map.of();
        }
        Map<UserId, List<ConteoDelDia>> resultado = new LinkedHashMap<>();
        conteoPort.conteoDiarioPorParticipante(participantes, desde, hasta).forEach((participanteId, dias) -> {
            if (!dias.isEmpty()) {
                resultado.put(participanteId, dias.stream().map(ConteoDiarioObjetivosService::aConteo).toList());
            }
        });
        return resultado;
    }

    private static ConteoDelDia aConteo(DiaRocas dia) {
        return new ConteoDelDia(dia.fecha(), dia.total(), dia.completadas());
    }
}
