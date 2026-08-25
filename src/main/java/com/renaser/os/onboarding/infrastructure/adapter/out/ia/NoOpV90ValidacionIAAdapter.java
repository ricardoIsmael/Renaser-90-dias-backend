package com.renaser.os.onboarding.infrastructure.adapter.out.ia;

import com.renaser.os.onboarding.application.ports.out.grabacionv90.ValidacionIAPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder: sin integracion de IA real en este alcance (decision explicita del encargo,
 * ver docs/MODULO_ONBOARDING.md). Siempre responde NO_DISPONIBLE — despues de 3 intentos,
 * {@code GrabacionV90.registrarSinResultado} hace caer la grabacion a REVISION_MANUAL.
 * Mismo estilo que {@code shared.infrastructure.storage.NoOpAlmacenamientoAdapter}.
 */
@Component
public class NoOpV90ValidacionIAAdapter implements ValidacionIAPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpV90ValidacionIAAdapter.class);

    @Override
    public ResultadoValidacionV90 validar(SolicitudValidacionV90 solicitud) {
        log.warn("ValidacionIAPort.validar(grabacion={}) placeholder: sin integracion de IA todavia "
                + "(alcance de este modulo, ver docs/MODULO_ONBOARDING.md). Cae a revision manual tras 3 intentos.",
                solicitud.grabacionId());
        return ResultadoValidacionV90.noDisponible();
    }
}
