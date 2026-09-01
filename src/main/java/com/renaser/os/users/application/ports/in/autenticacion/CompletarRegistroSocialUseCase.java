package com.renaser.os.users.application.ports.in.autenticacion;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import jakarta.validation.constraints.NotBlank;

/**
 * Segundo paso del alta social (docs/MODULO_AUTH.md §6.10, decision del dueño del proyecto
 * 2026-09-01, D-65). Recibe el token que devolvio {@code POST /auth/social} cuando la identidad
 * era nueva, y recien ACA se abre la {@code AccountRequest} — antes se abria en la misma llamada
 * que verificaba la identidad contra el proveedor, y como el {@code code} de OAuth es de un solo
 * uso, la app nunca llegaba a mostrarle a la persona un formulario de confirmacion con sus datos
 * ya prellenados: para cuando el backend conocia el correo, el `code` ya estaba gastado.
 *
 * <p>Con este paso intermedio, {@code IniciarSesionConProveedorUseCase} retiene la identidad ya
 * verificada en Redis (10 minutos, igual que el OTP de alta) y este caso de uso la recupera para
 * recien ahi armar la solicitud, con el mismo camino que ya usa el autoregistro por formulario
 * ({@code SubmitAccountRequestUseCase}) — el alta social sigue sin ser un camino paralelo.
 */
public interface CompletarRegistroSocialUseCase {

    /**
     * @throws com.renaser.os.shared.domain.RegistroPendienteSocialInvalidoException si el token
     *         no existe, ya vencio, o ya se uso — hay que rehacer el flujo del proveedor social
     *         desde el principio, porque el {@code code} original ya se gasto
     */
    AccountRequestId completar(CompletarRegistroSocialCommand command);

    /**
     * {@code registroPendienteToken} es la UNICA fuente del correo y del sujeto del proveedor:
     * los dos viven en el registro que retuvo Redis, NUNCA en este comando. Si el cliente
     * pudiera mandar el correo aca, cualquiera completaria un registro con el correo de otra
     * persona — es exactamente el agujero de apropiacion de cuenta que docs/MODULO_AUTH.md §6.4
     * y D-60 vienen evitando. Mismo blindaje por compilador que el {@code role} ausente del alta
     * publica (CLAUDE.MD §5.3.3).
     *
     * <p>{@code fullName} SI viaja del cliente: la persona puede corregir como se escribe su
     * nombre respecto de lo que devolvio el proveedor. {@code phone}/{@code city} son opcionales,
     * igual que en el alta por formulario (D-61) — se piden despues, en la Ficha Inicial del
     * onboarding, y si vienen igual se guardan.
     */
    record CompletarRegistroSocialCommand(
            @NotBlank String registroPendienteToken,
            @NotBlank String fullName,
            String phone,
            String city,
            String requestIp) {

        public CompletarRegistroSocialCommand {
            SelfValidating.validateConstructorArgs(CompletarRegistroSocialCommand.class, registroPendienteToken,
                    fullName, phone, city, requestIp);
        }

        /** El token de continuacion es una credencial de un solo uso: nunca al log. */
        @Override
        public String toString() {
            return "CompletarRegistroSocialCommand[registroPendienteToken=oculto, fullName=" + fullName + "]";
        }
    }
}
