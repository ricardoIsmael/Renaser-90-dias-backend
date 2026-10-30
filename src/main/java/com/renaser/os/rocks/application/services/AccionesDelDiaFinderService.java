package com.renaser.os.rocks.application.services;

import com.renaser.os.evidence.api.EvidenciasDeRocasFinder;
import com.renaser.os.rocks.api.AccionesDelDiaFinder;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Las acciones del día de una persona en un rango, con la evidencia de cada una (spec 005, R-5). Delega: las rocas son
 * las mismas que lee {@code GET /rocks/upcoming} ({@code deParticipanteEntreFechas}, una sola consulta) y la evidencia
 * la resuelve {@code evidence}. No autoriza ni pide el estado de la cuenta: quien llama ya lo hizo.
 */
@Service
class AccionesDelDiaFinderService implements AccionesDelDiaFinder {

    private final LoadRocaDiariaPort loadRocaDiariaPort;
    private final EvidenciasDeRocasFinder evidencias;

    AccionesDelDiaFinderService(LoadRocaDiariaPort loadRocaDiariaPort, EvidenciasDeRocasFinder evidencias) {
        this.loadRocaDiariaPort = loadRocaDiariaPort;
        this.evidencias = evidencias;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccionDelDia> accionesEntre(UserId aprendizId, LocalDate desde, LocalDate hasta) {
        List<RocaDiaria> rocas = loadRocaDiariaPort.deParticipanteEntreFechas(aprendizId, desde, hasta);
        // Solo las completadas pueden tener evidencia (R-02): no se pregunta por las demas.
        Map<UUID, UUID> deLasCompletadas = evidencias.principalesDe(rocas.stream()
                .filter(RocaDiaria::completada).map(roca -> roca.id().value()).toList());
        return rocas.stream()
                .map(roca -> new AccionDelDia(roca.id().value(), roca.fecha(), roca.titulo(), roca.completada(),
                        deLasCompletadas.get(roca.id().value()), roca.horaInicio()))
                .toList();
    }
}
