package com.renaser.os.shared.domain;

/**
 * Excepcion de negocio: el actor no tiene permiso para la operacion.
 *
 * NO conoce codigos HTTP (CLAUDE.MD §5.4.4). Traducirla a un 403 es
 * responsabilidad exclusiva de shared/web/GlobalExceptionHandler.
 */
public class NotAuthorizedException extends RuntimeException {

    public NotAuthorizedException(String message) {
        super(message);
    }
}
