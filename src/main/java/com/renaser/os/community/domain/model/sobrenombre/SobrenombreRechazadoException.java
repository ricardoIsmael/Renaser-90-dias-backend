package com.renaser.os.community.domain.model.sobrenombre;

/**
 * Spec 046: por qué no se acepta un sobrenombre. El mensaje es el que ve la persona (tuteo, sin jerga); el código lo usa la app para
 * marcar el campo.
 */
public class SobrenombreRechazadoException extends RuntimeException {

    public enum Motivo {
        /** De 3 a 20 caracteres, sin espacios al principio ni al final ni dos seguidos. */
        LARGO,
        /** Solo letras (con tildes y ñ), números, espacio, punto y guion. */
        CARACTERES,
        /** Tiene una palabra que no se puede usar. */
        PROHIBIDO,
        /** Ya lo tiene otra persona de su formación, o es el nombre real de otra persona. */
        REPETIDO,
        /** Lo cambió hace menos de 30 días (D5). */
        ESPERA
    }

    private final Motivo motivo;

    public SobrenombreRechazadoException(Motivo motivo, String mensaje) {
        super(mensaje);
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
