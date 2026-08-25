package com.renaser.os.onboarding.application.ports.in.estado;

import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.shared.domain.UserId;

/**
 * Obtiene el estado de onboarding del usuario, INICIALIZANDOLO si es la primera vez que lo
 * abre (decision de este modulo: no hay un endpoint separado "crear onboarding" en el
 * encargo — el primer GET crea la fila, igual que el viejo comportamiento implicito de
 * "el onboarding arranca solo con abrir la pantalla"). Ver docs/MODULO_ONBOARDING.md.
 */
public interface ObtenerEstadoOnboardingUseCase {

    EstadoOnboarding obtener(UserId usuarioId);
}
