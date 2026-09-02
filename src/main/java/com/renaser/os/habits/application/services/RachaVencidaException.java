package com.renaser.os.habits.application.services;

/**
 * Señal de que el plazo para cerrar la racha sin celular venció durante el intento de
 * cierre — la racha ya quedó marcada {@code EXPIRADA} (y el registro liberado) y
 * guardada antes de lanzar esta excepción.
 *
 * <p>Mismo motivo que {@link RegistroExpiradoException} (ver su Javadoc): existe para
 * poder marcar {@code noRollbackFor} en {@link RachaService#cerrar} sin también
 * inhibir el rollback de otros {@link IllegalStateException} que ese método puede
 * lanzar en otros puntos (guard clauses de {@code RachaSinCelular}/{@code
 * RegistroHabito} que sí deben revertir su escritura si fallan) — C-9, docs/informes/
 * auditoria-seguridad-concurrencia-2026-09-01.html.
 *
 * <p>Extiende {@link IllegalStateException} para no tocar el contrato HTTP: sigue
 * cayendo en el mismo {@code GlobalExceptionHandler} -&gt; 409 de siempre (CLAUDE.MD §8).
 */
final class RachaVencidaException extends IllegalStateException {

    RachaVencidaException(String message) {
        super(message);
    }
}
