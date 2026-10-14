package com.renaser.os.onboarding.api;

import com.renaser.os.shared.domain.UserId;

/**
 * Deja completo el onboarding de una cuenta de prueba de «ver como rol» (spec 002, fase 2, US5; D-270): términos,
 * pacto aceptado y firmado, rocas sincronizadas y completado. Sin eso, la app le mostraría a «ver como Aprendiz» la
 * ficha inicial en vez de su inicio (la app decide con {@code GET /onboarding/state}).
 *
 * <p>Solo para cuentas de prueba (400 si no lo es) y solo lo pide Soporte TI activo (403). Idempotente.
 */
public interface OnboardingDeCuentaDePruebaUseCase {

    void completar(UserId soporteTi, UserId cuentaDePrueba);
}
