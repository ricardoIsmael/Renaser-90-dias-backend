package com.renaser.os.habits.application.services;

/**
 * Señal de que el registro venció durante el intento de completarlo — el registro ya
 * quedó marcado {@code EXPIRADO} y guardado antes de lanzar esta excepción.
 *
 * <p>Existe como tipo propio (en vez de un {@link IllegalStateException} plano) por una
 * sola razón mecánica: {@link RegistroService#completar} necesita que ESTA excepción
 * puntual no revierta la expiración recién guardada (C-9, docs/informes/
 * auditoria-seguridad-concurrencia-2026-09-01.html), vía {@code noRollbackFor} en el
 * {@code @Transactional} del método — pero sin aflojar el rollback de cualquier OTRO
 * {@link IllegalStateException} que ese mismo método pueda lanzar (los guard clauses de
 * {@code RegistroHabito.completar}, que sí deben revertir su escritura si fallan). Marcar
 * {@code noRollbackFor} sobre {@code IllegalStateException.class} a secas habría tapado
 * esos otros casos también — este tipo acota el efecto a un único punto de lanzamiento.
 *
 * <p>Extiende {@link IllegalStateException} a propósito: {@code GlobalExceptionHandler}
 * ya mapea esa jerarquía a 409 (CLAUDE.MD §8, el contrato con la app RN no cambia) y no
 * hace falta agregar un handler nuevo para este caso.
 */
final class RegistroExpiradoException extends IllegalStateException {

    RegistroExpiradoException(String message) {
        super(message);
    }
}
