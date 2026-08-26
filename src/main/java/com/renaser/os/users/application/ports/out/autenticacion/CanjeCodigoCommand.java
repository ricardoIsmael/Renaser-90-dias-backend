package com.renaser.os.users.application.ports.out.autenticacion;

import com.renaser.os.shared.application.SelfValidating;
import jakarta.validation.constraints.NotBlank;

/**
 * Entrada de {@link VerificadorIdentidadProveedor#verificar}: lo que la app RN recibe del
 * navegador del sistema tras el redirect de OAuth (docs/MODULO_AUTH.md §6.1, paso 3) y manda al
 * backend en {@code POST /api/v1/auth/social}. Self-validating (CLAUDE.MD §5.4.3 nivel 2): si
 * mañana un scheduler o un listener invocan un adaptador con este comando, no pueden hacerlo con
 * campos vacios, vengan de donde vengan.
 *
 * <p>{@code codeVerifier} es obligatorio en los tres proveedores: PKCE no es opcional para un
 * cliente publico (§6.1).
 */
public record CanjeCodigoCommand(@NotBlank String code, @NotBlank String codeVerifier,
                                  @NotBlank String redirectUri) {

    public CanjeCodigoCommand {
        SelfValidating.validateConstructorArgs(CanjeCodigoCommand.class, code, codeVerifier, redirectUri);
    }

    /** El `code` y el `code_verifier` son credenciales de un solo uso: nunca al log. */
    @Override
    public String toString() {
        return "CanjeCodigoCommand[code=oculto, codeVerifier=oculto, redirectUri=" + redirectUri + "]";
    }
}
