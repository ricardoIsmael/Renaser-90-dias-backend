package com.renaser.os.rocks.infrastructure.adapter.out.medicion;

import com.renaser.os.onboarding.api.MedicionDelMapaFinder;
import com.renaser.os.onboarding.api.MedicionDelMapaFinder.MedicionDelMapa;
import com.renaser.os.rocks.application.ports.out.medicion.ConsultarMedicionDelMapaPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

/**
 * Delega en el contrato publico de {@code onboarding} (D-41): que declaro la persona en el Mapa lo
 * responde el modulo dueno de {@code respuestas_onboarding}, no una query de {@code rocks} contra
 * una tabla ajena.
 */
@Component
class MedicionDelMapaAdapter implements ConsultarMedicionDelMapaPort {

    private final MedicionDelMapaFinder finder;

    MedicionDelMapaAdapter(MedicionDelMapaFinder finder) {
        this.finder = finder;
    }

    @Override
    public MedicionDelParticipante deParticipante(UserId participanteId) {
        MedicionDelMapa medicion = finder.delParticipante(participanteId);
        return new MedicionDelParticipante(medicion.saludTipo(), medicion.saludUnidad(), medicion.negocioTipo(),
                medicion.negocioPeriodo(), medicion.relacionesBase(), medicion.relacionesMeta());
    }
}
