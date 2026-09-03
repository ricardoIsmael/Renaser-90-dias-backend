package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase.ResultadoLoginSocial;

/**
 * Respuesta de {@code POST /api/v1/auth/social} cuando la identidad es NUEVA (docs/MODULO_AUTH.md
 * §6.10, D-65, 2026-09-01). Todavia no existe ninguna {@code AccountRequest}: {@code email} y
 * {@code fullName} son los datos que devolvio el proveedor, para que la app prellene el
 * formulario de confirmacion, y {@code registroPendienteToken} es lo que hay que reenviar a
 * {@code POST /auth/social/complete} para recien ahi abrir la solicitud.
 *
 * <p>El token es de un solo uso y vence a los 10 minutos — si la persona tarda mas que eso en
 * confirmar el formulario, tiene que rehacer el flujo del proveedor desde el principio (el
 * {@code code} de OAuth original ya esta gastado de todas formas).
 */
public record RegistroPendienteSocialResponse(String registroPendienteToken, String email, String fullName) {

    public static RegistroPendienteSocialResponse from(ResultadoLoginSocial.RegistroPendiente resultado) {
        return new RegistroPendienteSocialResponse(resultado.token(), resultado.email(), resultado.fullName());
    }
}
