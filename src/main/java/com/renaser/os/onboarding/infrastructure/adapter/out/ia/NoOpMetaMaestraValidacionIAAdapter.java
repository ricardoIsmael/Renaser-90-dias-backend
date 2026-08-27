package com.renaser.os.onboarding.infrastructure.adapter.out.ia;

import com.renaser.os.onboarding.application.ports.out.metamaestra.ValidacionMetaMaestraPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder: sin integracion de IA real en este alcance (mismo estado del modulo que
 * {@code NoOpV90ValidacionIAAdapter}). Siempre responde NO_DISPONIBLE — el caso de uso lo
 * trata como fallo tecnico y devuelve PENDIENTE_DE_REVISION (fail-open, nunca bloquea al
 * aprendiz), replicando el comportamiento documentado del backend viejo.
 */
@Component
public class NoOpMetaMaestraValidacionIAAdapter implements ValidacionMetaMaestraPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpMetaMaestraValidacionIAAdapter.class);

    @Override
    public ResultadoValidacionMetaMaestra validar(String texto) {
        log.warn("ValidacionMetaMaestraPort.validar(...) placeholder: sin integracion de IA todavia "
                + "(alcance de este modulo, ver docs/MODULO_ONBOARDING.md). El caso de uso cae a "
                + "PENDIENTE_DE_REVISION (fail-open).");
        return ResultadoValidacionMetaMaestra.noDisponible();
    }
}
