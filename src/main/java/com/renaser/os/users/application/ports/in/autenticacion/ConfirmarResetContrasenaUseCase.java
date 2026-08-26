package com.renaser.os.users.application.ports.in.autenticacion;

import com.renaser.os.shared.application.SelfValidating;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public interface ConfirmarResetContrasenaUseCase {

    /**
     * Valida el token contra el almacen de un solo uso, fija la contrasena nueva y cierra TODAS
     * las sesiones activas del usuario (docs/MODULO_AUTH.md §4.1) — cambiar la contrasena por
     * sospecha de robo tiene que invalidar cualquier sesion que el atacante ya tenga abierta,
     * no solo prevenir logins futuros.
     *
     * @throws com.renaser.os.shared.domain.TokenResetInvalidoException si el token no existe, ya
     * vencio, o ya se uso
     */
    void confirmar(ConfirmarResetContrasenaCommand command);

    /** Mismo minimo de 12 caracteres que {@code IniciarSesionUseCase} usa para login (CLAUDE.MD §5.4.3 nivel 2, docs/MODULO_AUTH.md §7.2). */
    record ConfirmarResetContrasenaCommand(@NotBlank String token,
                                            @NotBlank @Size(min = 12, max = 200) String contrasenaNueva) {
        public ConfirmarResetContrasenaCommand {
            SelfValidating.validateConstructorArgs(ConfirmarResetContrasenaCommand.class, token, contrasenaNueva);
        }

        /** Ni el token ni la contrasena nueva se loguean nunca: son credenciales en claro durante la request. */
        @Override
        public String toString() {
            return "ConfirmarResetContrasenaCommand[token=oculto, contrasenaNueva=oculta]";
        }
    }
}
