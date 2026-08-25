package com.renaser.os.onboarding.application.ports.in.grabacionv90;

import com.renaser.os.shared.domain.UserId;

/**
 * El trabajo REAL de un intento de validacion (llama a {@code ValidacionIAPort} y actualiza
 * el estado de la grabacion). Lo invoca {@code DespacharValidacionV90Port} desde un hilo
 * separado (@Async) — nunca un controller REST directamente (por eso no tiene comando
 * self-validating con @NotNull en un DTO web: no es una entrada HTTP).
 */
public interface ProcesarValidacionV90UseCase {

    void procesar(UserId usuarioId, long grabacionId);
}
