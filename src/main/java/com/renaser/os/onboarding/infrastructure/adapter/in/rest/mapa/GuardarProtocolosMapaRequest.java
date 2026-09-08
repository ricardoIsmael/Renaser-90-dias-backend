package com.renaser.os.onboarding.infrastructure.adapter.in.rest.mapa;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** V07 completa. Mismo criterio que las acciones: la vista entera, no un protocolo suelto. */
public record GuardarProtocolosMapaRequest(@NotNull @Size(max = 3) List<@Valid Protocolo> protocols) {

    public record Protocolo(@NotBlank String protocolId,
                             @NotBlank String pattern,
                             @NotBlank String trigger,
                             @NotBlank String currentBehavior,
                             @NotBlank String alternativeResponse) {
    }
}
