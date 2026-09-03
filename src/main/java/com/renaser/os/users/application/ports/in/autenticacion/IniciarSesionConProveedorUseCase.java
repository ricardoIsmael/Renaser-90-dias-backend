package com.renaser.os.users.application.ports.in.autenticacion;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import com.renaser.os.users.domain.model.user.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Login social (Google/Apple/Facebook), docs/MODULO_AUTH.md §6. Resuelve la identidad via
 * {@code VerificadorIdentidadProveedor} y decide entre varios caminos SIN crear un usuario en
 * silencio (§6.4):
 *
 * <ul>
 *   <li>La identidad ya esta vinculada a un {@code User} → sesion.</li>
 *   <li>Es la primera vez que se ve esa identidad → se retiene en Redis (§6.10, D-65) y la app
 *       muestra un formulario de confirmacion; recien cuando se confirma
 *       ({@code CompletarRegistroSocialUseCase}) se abre la {@code AccountRequest}.</li>
 * </ul>
 */
public interface IniciarSesionConProveedorUseCase {

    ResultadoLoginSocial iniciarSesion(IniciarSesionConProveedorCommand command);

    /**
     * Desde D-65 (2026-09-01) este comando SOLO verifica la identidad — ya no lleva
     * {@code phone}/{@code city}: esos datos se piden en {@code CompletarRegistroSocialCommand},
     * el paso siguiente, porque el alta ya no ocurre en esta misma llamada (ver el javadoc de
     * {@link ResultadoLoginSocial.RegistroPendiente}).
     */
    record IniciarSesionConProveedorCommand(
            @NotNull ProveedorIdentidad proveedor,
            @NotBlank String code,
            @NotBlank String codeVerifier,
            @NotBlank String redirectUri,
            String requestIp) {

        public IniciarSesionConProveedorCommand {
            SelfValidating.validateConstructorArgs(IniciarSesionConProveedorCommand.class, proveedor, code,
                    codeVerifier, redirectUri, requestIp);
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
     * CLAUDE.MD §5.4.7): el conjunto de resultados es cerrado, y el compilador obliga al
     * controller a cubrirlos todos — uno establece sesion, los otros tres no.
     *
     * <p>Las tres variantes que no son sesion son <b>estados normales del flujo, no errores</b>,
     * y por eso son valores de retorno y no excepciones.
     */
    sealed interface ResultadoLoginSocial permits ResultadoLoginSocial.SesionIniciada,
            ResultadoLoginSocial.RegistroPendiente, ResultadoLoginSocial.SolicitudEnRevision,
            ResultadoLoginSocial.CuentaExistenteSinVinculo {

        /** La identidad ya estaba vinculada: se abre sesion. */
        record SesionIniciada(User usuario) implements ResultadoLoginSocial {
        }

        /**
         * Primera vez que se ve esta identidad: TODAVIA no se abrio ninguna {@code
         * AccountRequest} (docs/MODULO_AUTH.md §6.10, D-65, 2026-09-01). {@code token} es el
         * registro de continuacion (opaco, de un solo uso, vence a los 10 minutos — igual que el
         * OTP de alta) que la app tiene que reenviar a {@code POST /auth/social/complete} junto
         * con lo que la persona confirme en el formulario. {@code email}/{@code fullName} son
         * los datos que devolvio el proveedor, para prellenar ese formulario.
         *
         * <p>El motivo de este paso intermedio: el {@code code} de OAuth es de un solo uso y la
         * app no conoce el correo/nombre hasta que el backend lo canjea, asi que abrir la
         * solicitud en la misma llamada que verifica la identidad nunca le daba a la app la
         * oportunidad de mostrar un formulario de confirmacion — para cuando el backend conocia
         * esos datos, ya habia decidido que hacer con ellos.
         */
        record RegistroPendiente(String token, String email, String fullName) implements ResultadoLoginSocial {
        }

        /**
         * Esta identidad ya abrio una solicitud y sigue PENDIENTE. Volver a tocar "Continuar con
         * Google" mientras un admin no decide no crea otra solicitud ni es un error: la persona
         * simplemente todavia no fue aprobada.
         */
        record SolicitudEnRevision(AccountRequestId solicitudId) implements ResultadoLoginSocial {
        }

        /**
         * Existe un usuario con ese correo, pero esta identidad social no esta vinculada a el.
         * <b>No se vincula automaticamente</b>: hacerlo por coincidencia de correo es exactamente
         * el camino por el que alguien se apodera de una cuenta ajena (docs/MODULO_AUTH.md §6.4).
         * La salida es que la persona pruebe que es la dueña por otro canal — entrar con su
         * contrasena, o recuperarla por correo.
         */
        record CuentaExistenteSinVinculo(ProveedorIdentidad proveedor) implements ResultadoLoginSocial {
        }
    }
}
