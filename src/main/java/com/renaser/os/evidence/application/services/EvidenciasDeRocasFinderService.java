package com.renaser.os.evidence.application.services;

import com.renaser.os.evidence.api.EvidenciasDeRocasFinder;
import com.renaser.os.evidence.application.ports.out.evidencia.LoadEvidenciaPort;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Mismo criterio que {@code EntregasPorRegistroFinderService}: consulta de salida, sin estado. */
@Service
class EvidenciasDeRocasFinderService implements EvidenciasDeRocasFinder {

    private final LoadEvidenciaPort loadEvidenciaPort;

    EvidenciasDeRocasFinderService(LoadEvidenciaPort loadEvidenciaPort) {
        this.loadEvidenciaPort = loadEvidenciaPort;
    }

    @Override
    public Map<UUID, UUID> principalesDe(Collection<UUID> rocasDiariasIds) {
        return loadEvidenciaPort.evidenciasPrincipalesDeRocas(rocasDiariasIds);
    }
}
