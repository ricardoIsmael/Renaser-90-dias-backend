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
     * CLAUDE.MD §5.4.7): el conjunto de resultados es cerrado, y el compilador obliga al
     * controller a cubrirlos todos — uno establece sesion, los otros tres no.
     *
     * <p>Las tres variantes que no son sesion son <b>estados normales del flujo, no errores</b>,
     * y por eso son valores de retorno y no excepciones: "tu solicitud sigue en revision" es una
     * respuesta legitima que la app tiene que poder mostrar como tal. Antes las tres colapsaban
     * en el mismo {@code IllegalStateException} generico (A-7).
     */
    sealed interface ResultadoLoginSocial permits ResultadoLoginSocial.SesionIniciada,
            ResultadoLoginSocial.SolicitudCreada, ResultadoLoginSocial.SolicitudEnRevision,
            ResultadoLoginSocial.CuentaExistenteSinVinculo {

        /** La identidad ya estaba vinculada: se abre sesion. */
        record SesionIniciada(User usuario) implements ResultadoLoginSocial {
        }

        /** Primera vez que se ve esta identidad: se abrio una solicitud, la aprueba un ADMIN. */
        record SolicitudCreada(AccountRequestId solicitudId) implements ResultadoLoginSocial {
        }

        /**
         * Esta identidad ya abrio una solicitud y sigue PENDIENTE. Volver a tocar "Continuar con
         * Google" mientras un admin no decida no crea otra solicitud ni es un error: la persona
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
