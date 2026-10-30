package com.renaser.os.rocks.domain.model.cierre;

/**
 * Se quiso cerrar un día que no es hoy en la zona de la persona (spec 017). Es la regla D-259 aplicada al cierre: lo del día se
 * registra durante su día; un día que ya terminó no se cierra tarde.
 *
 * <p>Extiende {@link IllegalStateException} a propósito: {@code GlobalExceptionHandler} la responde como 409 con este mensaje, y
 * la app ya reconoce «ese día ya cerró» como rechazo por día cerrado (recarga y avisa; no reasigna el intento).
 */
public final class CierreDeOtroDiaException extends IllegalStateException {

    public CierreDeOtroDiaException() {
        super("Ese día ya cerró y no se puede cerrar tarde.");
    }
}
