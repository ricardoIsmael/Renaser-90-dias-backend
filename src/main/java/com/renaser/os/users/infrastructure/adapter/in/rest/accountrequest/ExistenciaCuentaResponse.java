package com.renaser.os.users.infrastructure.adapter.in.rest.accountrequest;

/**
 * Respuesta de {@code POST /account-requests/exists}, que consume "olvide mi contrasena" antes
 * de pedir el codigo. {@code exists == true} = hay cuenta o solicitud con ese correo.
 *
 * <p>Incluye solicitudes pendientes, no solo cuentas aprobadas: quien se registro y espera
 * aprobacion tambien puede necesitar recuperar el acceso (regla portada del repo viejo).
 */
public record ExistenciaCuentaResponse(boolean exists) {
}
