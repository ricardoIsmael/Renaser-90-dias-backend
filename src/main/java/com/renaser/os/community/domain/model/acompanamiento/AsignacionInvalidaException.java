package com.renaser.os.community.domain.model.acompanamiento;

/**
 * Una invariante temporal rechazada por el dominio. Se lanza antes de tocar la base para que
 * el fallo llegue como regla de negocio; la base repite la restricción con índices únicos
 * parciales porque un check-then-insert no gana una carrera (plan.md §3).
 */
public class AsignacionInvalidaException extends RuntimeException {

    public AsignacionInvalidaException(String message) {
        super(message);
    }
}
