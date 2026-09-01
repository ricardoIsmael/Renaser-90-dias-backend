package com.renaser.os.users.infrastructure.adapter.in.rest.accountrequest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sin campo role a proposito: CLAUDE.MD §5.3.3. Tampoco lleva id de usuario: desde el
 * 2026-08-27 lo genera el backend internamente (D-49); el campo que el cliente mandaba antes
 * se llamaba supabaseUserId y ya no existe. {@code verificationToken} sale de
 * {@code POST /api/v1/auth/email-verification/confirm}.
 *
 * <p>{@code contrasena} (2026-08-27): la persona elige su clave al registrarse. El minimo de 12
 * es el mismo de todo el modulo de auth. {@link #toString()} la oculta — un log de este DTO no
 * puede filtrar una credencial en claro (CLAUDE.MD §5.4.9).
 *
 * <p>{@code phone} es OPCIONAL desde el 2026-09-01 (D-61), igual que {@code city}: el alta pide
 * lo minimo — correo, nombre y contrasena — y el telefono se recoge despues, en la Ficha Inicial
 * del onboarding, junto con el resto de los datos completos. Si viene, se guarda igual; lo que
 * dejo de existir es la obligacion. Antes era {@code @NotBlank} y el frontend, que ya habia
 * dejado de mandarlo, recibia 400 en cada registro. */
public record SubmitAccountRequestRequest(
        @NotBlank @Email String email,
        @NotBlank String fullName,
        String phone,
        String city,
        @NotBlank String verificationToken,
        @NotBlank @Size(min = 12, max = 200) String contrasena) {

    @Override
    public String toString() {
        return "SubmitAccountRequestRequest[email=" + email + ", fullName=" + fullName
                + ", verificationToken=oculto, contrasena=oculta]";
    }
}
