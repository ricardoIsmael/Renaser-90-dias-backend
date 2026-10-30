package com.renaser.os.evidence.application.services;

import com.renaser.os.evidence.api.RegistrosConTextoFinder;
import com.renaser.os.evidence.application.ports.out.evidencia.LoadEvidenciaPort;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Hermano de {@link RegistrosConEvidenciaFinderService}: una sola lectura, sin {@code @Transactional} propio
 * (la transacción corta del repositorio alcanza) y fuera de {@code EvidenciaService}, que ya tiene sus siete
 * métodos públicos.
 */
@Service
class RegistrosConTextoFinderService implements RegistrosConTextoFinder {

    private final LoadEvidenciaPort loadEvidenciaPort;

    RegistrosConTextoFinderService(LoadEvidenciaPort loadEvidenciaPort) {
        this.loadEvidenciaPort = loadEvidenciaPort;
    }

    @Override
    public Set<UUID> conTextoDeAlMenos(Collection<UUID> registrosHabitoIds, int largoMinimo) {
        return loadEvidenciaPort.registrosHabitoConTextoDeAlMenos(registrosHabitoIds, largoMinimo);
    }
}
