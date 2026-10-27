package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.api.MapaActivoFinder;
import com.renaser.os.onboarding.application.ports.out.mapa.EtapaOnboardingPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa {@link MapaActivoFinder} con la marca de etapa por flujo (V41): una sola consulta, la misma
 * que {@code GET /api/v1/mapa-renacimiento} devuelve como {@code stageCompleted}.
 */
@Service
public class MapaActivoService implements MapaActivoFinder {

    private final EtapaOnboardingPort etapaOnboardingPort;

    public MapaActivoService(EtapaOnboardingPort etapaOnboardingPort) {
        this.etapaOnboardingPort = etapaOnboardingPort;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean activo(UserId usuarioId) {
        return etapaOnboardingPort.flujosCompletados(usuarioId).contains(MapaRenacimientoService.FLUJO);
    }
}
