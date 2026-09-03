package com.renaser.os.onboarding.infrastructure.adapter.in.rest.metamaestra;

import java.util.List;

/**
 * Shape identico al que ya espera el frontend real ({@code SixPsValidation} en
 * {@code C:\renaserPlayStore\src\services\onboarding.ts}): {@code accepted}/{@code
 * missingPs}/{@code feedback}/{@code pendingReview}.
 *
 * <p>Los cuatro campos se conservan a proposito aunque hoy solo tengan un valor posible: el
 * cliente ya los lee, y cambiar la forma lo romperia sin darle nada a cambio. Con el
 * veredicto de IA fuera del alcance (2026-09-03), la respuesta es siempre la misma —
 * aceptada, sin Ps faltantes y sin revision pendiente.
 */
public record ValidacionMetaMaestraResponse(boolean accepted, List<String> missingPs, String feedback,
                                             boolean pendingReview) {

    /**
     * La unica respuesta posible. {@code pendingReview} va en {@code false}, no en
     * {@code true}: no hay nada pendiente de revisar. Decir lo contrario le prometeria al
     * aprendiz una revision que nadie va a hacer.
     */
    public static ValidacionMetaMaestraResponse aceptada() {
        return new ValidacionMetaMaestraResponse(true, List.of(), "", false);
    }
}
