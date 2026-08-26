package com.renaser.os.shared.domain;

/**
 * El proveedor de login social (Google/Apple/Facebook) rechazo el intercambio de codigo, o la
 * identidad devuelta no se pudo verificar (firma invalida, expirada, `iss`/`aud` que no
 * corresponden). Mensaje deliberadamente generico — el detalle real (motivo del proveedor,
 * causa de la falla criptografica) se loguea en el adaptador, nunca viaja al cliente.
 */
public class IdentidadProveedorInvalidaException extends RuntimeException {

    public IdentidadProveedorInvalidaException(String proveedor) {
        super("No se pudo verificar la identidad con " + proveedor);
    }

    public IdentidadProveedorInvalidaException(String proveedor, Throwable cause) {
        super("No se pudo verificar la identidad con " + proveedor, cause);
    }
}
