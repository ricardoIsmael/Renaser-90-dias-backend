package com.renaser.os.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * El formato de error de siempre ({@link ApiErrorResponse}: {@code message} y {@code timestamp}) más dos campos
 * aditivos para los errores que la app nueva tiene que reconocer sin leer el texto (spec 002, contrato §2):
 * {@code codigo} ({@code FUNCIONALIDAD_APAGADA}, {@code CONSOLA_BLOQUEADA}, {@code REAUTENTICACION_REQUERIDA},
 * {@code CONTRASENA_INCORRECTA}) y, desde la spec 019, los motivos de un 403 ({@code PROGRAMA_NO_ACTIVADO},
 * {@code CUENTA_SUSPENDIDA}, {@code SIN_PERMISO}; ver {@code NotAuthorizedException}) y,
 * solo para el primero, {@code funcionalidad}. Una app que no los conoce sigue mostrando {@code message}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorConCodigo(String message, Instant timestamp, String codigo, String funcionalidad) {

    public static ApiErrorConCodigo of(String message, String codigo, String funcionalidad) {
        return new ApiErrorConCodigo(message, Instant.now(), codigo, funcionalidad);
    }
}
