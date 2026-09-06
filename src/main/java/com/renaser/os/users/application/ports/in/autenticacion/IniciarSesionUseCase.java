package com.renaser.os.users.application.ports.in.autenticacion;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.users.domain.model.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public interface IniciarSesionUseCase {

    /**
     * Devuelve el {@link User} completo (mismo patron que {@code GetMyProfileUseCase}) para que
     * el controller arme la respuesta con UNA sola llamada, sin un segundo viaje a `users` solo
     * para volver a leer lo que este metodo ya cargo internamente.
     *
     * @throws com.renaser.os.shared.domain.CredencialesInvalidasException si no coincide
     */
    User iniciarSesion(IniciarSesionCommand command);

    /**
     * {@code requestIp} puede ser nula: solo alimenta el limite por IP, que es la segunda
     * barrera. La que de verdad protege una cuenta es el limite por email, y esa no depende
     * de la IP — un atacante rota direcciones, pero no puede rotar el correo de su victima.
     */
    record IniciarSesionCommand(@NotBlank @Email String email, @NotBlank String contrasena,
                                 String requestIp) {
        public IniciarSesionCommand {
            SelfValidating.validateConstructorArgs(IniciarSesionCommand.class, email, contrasena, requestIp);
        }

        /** Nunca se loguea junto al email: es una credencial en claro durante la request. */
        @Override
        public String toString() {
            return "IniciarSesionCommand[email=" + email + ", contrasena=oculta]";
        }
    }
}
