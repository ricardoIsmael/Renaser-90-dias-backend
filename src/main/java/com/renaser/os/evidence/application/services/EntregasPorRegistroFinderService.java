package com.renaser.os.evidence.application.services;

import com.renaser.os.evidence.api.EntregaDeEvidencia;
import com.renaser.os.evidence.api.EntregasPorRegistroFinder;
import com.renaser.os.evidence.application.ports.out.evidencia.LoadEvidenciaPort;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Mismo criterio que {@code RegistrosConEvidenciaFinderService}: consulta de salida, sin estado. */
@Service
class EntregasPorRegistroFinderService implements EntregasPorRegistroFinder {

    private final LoadEvidenciaPort loadEvidenciaPort;

    EntregasPorRegistroFinderService(LoadEvidenciaPort loadEvidenciaPort) {
        this.loadEvidenciaPort = loadEvidenciaPort;
    }

    @Override
    public Map<UUID, EntregaDeEvidencia> porRegistros(Collection<UUID> registrosHabitoIds) {
        return loadEvidenciaPort.entregasDeRegistros(registrosHabitoIds);
    }
}
