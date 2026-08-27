package com.renaser.os.onboarding.infrastructure.adapter.in.rest.metamaestra;

import com.renaser.os.onboarding.application.ports.in.metamaestra.ValidarMetaMaestraUseCase.ResultadoMetaMaestra;

import java.util.List;

/**
 * Shape identico al que ya espera el frontend real ({@code SixPsValidation} en
 * {@code C:\renaserPlayStore\src\services\onboarding.ts}): {@code accepted}/{@code
 * missingPs}/{@code feedback}, mas {@code pendingReview} cuando la IA no respondio.
 */
public record ValidacionMetaMaestraResponse(boolean accepted, List<String> missingPs, String feedback,
                                             boolean pendingReview) {

    public static ValidacionMetaMaestraResponse from(ResultadoMetaMaestra resultado) {
        return switch (resultado.veredicto()) {
            case APROBADA -> new ValidacionMetaMaestraResponse(true, resultado.pesFaltantes(),
                    resultado.feedback(), false);
            case RECHAZADA -> new ValidacionMetaMaestraResponse(false, resultado.pesFaltantes(),
                    resultado.feedback(), false);
            case PENDIENTE_DE_REVISION -> new ValidacionMetaMaestraResponse(true, List.of(), "", true);
        };
    }
}
