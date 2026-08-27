package com.renaser.os.users.infrastructure.adapter.in.rest.accountrequest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sin campo role a proposito: CLAUDE.MD §5.3.3. Sin supabaseUserId desde 2026-08-27: el
 * backend genera el id internamente (D-49). {@code verificationToken} sale de
 * {@code POST /api/v1/auth/email-verification/confirm}.
 *
 * <p>{@code contrasena} (2026-08-27): la persona elige su clave al registrarse. El minimo de 12
 * es el mismo de todo el modulo de auth. {@link #toString()} la oculta — un log de este DTO no
 * puede filtrar una credencial en claro (CLAUDE.MD §5.4.9). */
public record SubmitAccountRequestRequest(
        @NotBlank @Email String email,
        @NotBlank String fullName,
        @NotBlank String phone,
        String city,
        @NotBlank String verificationToken,
        @NotBlank @Size(min = 12, max = 200) String contrasena) {

    @Override
    public String toString() {
        return "SubmitAccountRequestRequest[email=" + email + ", fullName=" + fullName
                + ", verificationToken=oculto, contrasena=oculta]";
    }
}
