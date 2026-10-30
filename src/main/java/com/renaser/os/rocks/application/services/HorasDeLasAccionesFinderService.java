package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.api.HorasDeLasAccionesFinder;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Implementa {@link HorasDeLasAccionesFinder} (spec 015). Delega: las rocas son las mismas que lee
 * {@code GET /rocks/upcoming} ({@code deParticipanteEntreFechas}, una sola consulta). No autoriza ni pide el estado de
 * la cuenta: quien llama ya lo hizo.
 */
@Service
class HorasDeLasAccionesFinderService implements HorasDeLasAccionesFinder {

    private final LoadRocaDiariaPort loadRocaDiariaPort;

    HorasDeLasAccionesFinderService(LoadRocaDiariaPort loadRocaDiariaPort) {
        this.loadRocaDiariaPort = loadRocaDiariaPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HoraDeUnaAccion> entre(UserId aprendizId, LocalDate desde, LocalDate hasta) {
        return loadRocaDiariaPort.deParticipanteEntreFechas(aprendizId, desde, hasta).stream()
                .map(roca -> new HoraDeUnaAccion(roca.fecha(), roca.titulo(), roca.horaInicio(), roca.horaFin(),
                        roca.completada(), roca.completadaEn()))
                .toList();
    }
}
