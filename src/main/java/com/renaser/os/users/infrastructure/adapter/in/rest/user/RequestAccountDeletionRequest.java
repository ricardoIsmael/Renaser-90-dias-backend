package com.renaser.os.users.infrastructure.adapter.in.rest.user;

import jakarta.validation.constraints.NotBlank;

/** {@code confirmacion} debe ser exactamente "ELIMINAR" — ver RequestAccountDeletionUseCase. */
public record RequestAccountDeletionRequest(@NotBlank String confirmacion) {
}
