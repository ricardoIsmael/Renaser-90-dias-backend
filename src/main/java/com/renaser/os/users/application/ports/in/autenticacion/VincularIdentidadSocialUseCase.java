package com.renaser.os.users.application.ports.in.autenticacion;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Vincula una identidad social a la cuenta que <b>ya inicio sesion</b>
 * (docs/MODULO_AUTH.md §6.9). Es la salida que le faltaba al caso
 * {@code ResultadoLoginSocial.CuentaExistenteSinVinculo}: hasta ahora ese 409 le decia a la
 * persona "entra con tu contrasena", pero despues no existia ninguna forma de conectar su
 * Google — quedaba condenada a la contrasena para siempre.
 *
 * <p><b>Por que el vinculo es explicito y no automatico por coincidencia de correo</b> (decision
 * del dueño del proyecto, 2026-09-01): auto-vincular al ver que el correo del proveedor coincide
 * con el de una cuenta existente es exactamente el camino de apropiacion que §6.4 prohibe. El
 * costo de hacerlo explicito es una pantalla; el riesgo que cubre es que alguien entre a una
 * cuenta ajena. Es lo que recomiendan Auth0 y Clerk: autenticar <b>las dos</b> identidades antes
 * de unirlas — la nuestra con la sesion, la del proveedor con el {@code code}.
 *
 * <p>El correo del proveedor <b>no</b> tiene que coincidir con el de la cuenta: vincular un
 * Google personal a una cuenta con correo de trabajo es legitimo y los dos proveedores citados
 * lo permiten. Lo que impide que una misma cuenta de Google sirva a dos usuarios no es una
 * comparacion de correos, es la {@code UNIQUE (proveedor, sujeto_proveedor)} de la base — y el
 * chequeo explicito que la anticipa con un 409 entendible.
 */
public interface VincularIdentidadSocialUseCase {

    /**
     * Idempotente: si esa identidad ya estaba vinculada <b>a este mismo actor</b>, no hace nada
     * y no falla (el doble tap del cliente movil no puede ser un error).
     *
     * @throws com.renaser.os.shared.domain.IdentidadYaVinculadaException si la identidad ya
     *         pertenece a OTRO usuario
     * @throws com.renaser.os.shared.domain.IdentidadProveedorInvalidaException si el proveedor
     *         rechaza el {@code code} o no confirma el correo
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si el actor esta suspendido
     */
    void vincular(VincularIdentidadSocialCommand command);

    /**
     * Mismos campos que {@code IniciarSesionConProveedorCommand} salvo {@code phone}/{@code city}
     * — aca el usuario ya existe, no hay alta que completar — y con un campo que aquel no puede
     * tener: {@code actorId}, que <b>no viaja por HTTP</b>. Lo pone el controller desde la sesion
     * establecida, nunca el cliente: si el actor pudiera mandarse en el cuerpo (o en un header
     * como {@code X-Actor-Id}), cualquiera vincularia su Google a la cuenta de cualquier otro, que
     * es justamente el agujero que este caso de uso viene a cerrar.
     */
    record VincularIdentidadSocialCommand(
            @NotNull UserId actorId,
            @NotNull ProveedorIdentidad proveedor,
            @NotBlank String code,
            @NotBlank String codeVerifier,
            @NotBlank String redirectUri) {

        public VincularIdentidadSocialCommand {
            SelfValidating.validateConstructorArgs(VincularIdentidadSocialCommand.class, actorId, proveedor, code,
                    codeVerifier, redirectUri);
        }

        /** El `code` y el `code_verifier` son credenciales de un solo uso: nunca al log. */
        @Override
        public String toString() {
            return "VincularIdentidadSocialCommand[actorId=" + actorId + ", proveedor=" + proveedor
                    + ", code=oculto, codeVerifier=oculto, redirectUri=" + redirectUri + "]";
        }
    }
}
