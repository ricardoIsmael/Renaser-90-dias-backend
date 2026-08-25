package com.renaser.os.onboarding.api;

import com.renaser.os.shared.domain.UserId;

/**
 * Puerto de entrada publico: la unica forma en que otro modulo consulta el estado de
 * onboarding de un usuario (ej. `habits`/`rocks` gateando el arranque del programa hasta
 * que el onboarding este completo — todavia no refactorizado para consumir esto, lo hace
 * el dueño del repo cuando ese modulo lo necesite).
 */
public interface OnboardingEstadoFinder {

    /** false tambien si el usuario nunca abrio el onboarding (sin fila en estado_onboarding). */
    boolean completado(UserId usuarioId);

    /** El Pacto de Fase I se firma DENTRO del onboarding, no en `phasecontracts` (§HitoOnboarding). */
    boolean pactoFase1Firmado(UserId usuarioId);
}
