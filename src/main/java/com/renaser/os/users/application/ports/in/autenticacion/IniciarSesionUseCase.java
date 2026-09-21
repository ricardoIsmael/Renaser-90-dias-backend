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
     * {@code requestIp} puede ser nula, aunque en produccion no llega asi. Alimenta los DOS
     * limites del login: el de la IP y el de la pareja (origen, correo).
     *
     * <p><b>Corregido el 2026-09-21.</b> Aca decia que el limite que de verdad protege una
     * cuenta es el que cuelga del correo, "porque un atacante rota direcciones pero no puede
     * rotar el correo de su victima". Es cierto y es exactamente el problema: como el correo lo
     * elige quien llama, cualquiera podia gastar el cupo de una persona y dejarla sin entrar
     * —diez peticiones anonimas bastaban— sin acertar ninguna contrasena. Un contador anclado
     * solo al recurso atacado no lo defiende: lo vuelve una palanca contra su dueno. El tope
     * cuelga ahora de la pareja (origen, correo), y el detalle esta en
     * {@code OrigenDeLaPeticion} y en {@code AutenticacionService.requireDentroDelLimite}.
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
