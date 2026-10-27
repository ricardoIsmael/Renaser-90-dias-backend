package com.renaser.os.rocks.infrastructure.adapter.out.medicion;

import com.renaser.os.onboarding.api.MapaActivoFinder;
import com.renaser.os.rocks.application.ports.out.medicion.ConsultarEstadoDelMapaPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

/**
 * Delega en el contrato publico de {@code onboarding} (D-41): si el Mapa esta activo lo responde el modulo
 * dueno de {@code etapas_onboarding_completadas}, no una query de {@code rocks} contra una tabla ajena.
 */
@Component
class EstadoDelMapaAdapter implements ConsultarEstadoDelMapaPort {

    private final MapaActivoFinder finder;

    EstadoDelMapaAdapter(MapaActivoFinder finder) {
        this.finder = finder;
    }

    @Override
    public boolean activo(UserId participanteId) {
        return finder.activo(participanteId);
    }
}
