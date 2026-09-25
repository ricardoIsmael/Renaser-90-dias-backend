package com.renaser.os.rag.domain.model.propuesta;

/**
 * La propuesta ya no admite lo que se le pidio: vencio, se cancelo, o ya se ejecuto. El mensaje
 * es apto para mostrarse a la persona tal cual.
 *
 * <p>Extiende {@link IllegalStateException} para reusar el mapeo existente de
 * {@code GlobalExceptionHandler} a <b>409 Conflict</b>: el pedido es valido, pero choca con el
 * estado actual de la propuesta.
 */
public class PropuestaNoDisponibleException extends IllegalStateException {

    public PropuestaNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
