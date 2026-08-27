package com.renaser.os.users.infrastructure.adapter.in.rest.accountrequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo compartido por las tres consultas publicas de correo ({@code check-email},
 * {@code exists}, {@code verify-email}) — las tres reciben exactamente {@code {"email": "..."}},
 * igual que el {@code CheckEmailInput} unico del repo viejo.
 *
 * <p>Sin {@code @Email} de Bean Validation a proposito: {@code verify-email} debe poder
 * RESPONDER sobre un correo mal formado ({@code deliverable:false, reason:"formato"}), no
 * rechazarlo con un 400. El formato lo decide el value object {@code Email} en cada caso de uso,
 * que es donde la regla puede tener dos desenlaces distintos.
 */
public record ConsultarEmailRequest(@NotBlank @Size(max = 254) String email) {
}
