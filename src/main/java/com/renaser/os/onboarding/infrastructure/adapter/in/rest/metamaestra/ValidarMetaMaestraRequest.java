package com.renaser.os.onboarding.infrastructure.adapter.in.rest.metamaestra;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code text}: mismo nombre de campo que el backend viejo ({@code ValidateSmartTextInput}). */
public record ValidarMetaMaestraRequest(@NotBlank @Size(max = 3000) String text) {
}
