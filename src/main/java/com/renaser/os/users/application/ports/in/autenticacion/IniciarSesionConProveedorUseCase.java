package com.renaser.os.users.application.ports.in.autenticacion;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import com.renaser.os.users.domain.model.user.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Login social (Google/Apple/Facebook), docs/MODULO_AUTH.md §6. Resuelve la identidad via
 * {@code VerificadorIdentidadProveedor} y decide entre dos caminos SIN crear un usuario en
 * silencio (§6.4):
 *
 * <ul>
 *   <li>La identidad ya esta vinculada a un {@code User} → sesion.</li>
 *   <li>Es la primera vez que se ve esa identidad → se abre una {@code AccountRequest}, igual que
 *       el autoregistro por formulario; un ADMIN la aprueba despues.</li>
 * </ul>
 */
public interface IniciarSesionConProveedorUseCase {

    ResultadoLoginSocial iniciarSesion(IniciarSesionConProveedorCommand command);

    /**
     * {@code phone}/{@code city} son OPCIONALES a proposito: solo hacen falta en el camino de alta
     * (la identidad es nueva). Si son necesarios y faltan, el caso de uso rechaza con un mensaje
     * explicito — no se inventa un telefono. Ver la decision de diseño documentada en
     * docs/MODULO_AUTH.md §10 (A-8): Google no devuelve telefono, y
     * {@code SubmitAccountRequestCommand.phone} es {@code @NotBlank}, asi que este dato tiene que
     * viajar desde el cliente en algun punto del flujo social — hoy, en esta misma llamada.
     */
    record IniciarSesionConProveedorCommand(
            @NotNull ProveedorIdentidad proveedor,
            @NotBlank String code,
            @NotBlank String codeVerifier,
            @NotBlank String redirectUri,
            String phone,
            String city,
            String requestIp) {

        public IniciarSesionConProveedorCommand {
            SelfValidating.validateConstructorArgs(IniciarSesionConProveedorCommand.class, proveedor, code,
                    codeVerifier, redirectUri, phone, city, requestIp);
        }

        /** El `code` y el `code_verifier` son credenciales de un solo uso: nunca al log. */
        @Override
        public String toString() {
            return "IniciarSesionConProveedorCommand[proveedor=" + proveedor + ", code=oculto, "
                    + "codeVerifier=oculto, redirectUri=" + redirectUri + "]";
        }
    }

    /**
     * Sellada a proposito (mismo criterio que {@code DecisionPolitica}/{@code AccessDecision},
     * CLAUDE.MD §5.4.7): el conjunto de resultados es cerrado, y el controller debe cubrir ambos
     * casos — uno establece sesion, el otro no.
     */
    sealed interface ResultadoLoginSocial permits ResultadoLoginSocial.SesionIniciada,
            ResultadoLoginSocial.SolicitudCreada {

        record SesionIniciada(User usuario) implements ResultadoLoginSocial {
        }

        record SolicitudCreada(AccountRequestId solicitudId) implements ResultadoLoginSocial {
        }
    }
}
