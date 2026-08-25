package com.renaser.os.onboarding.application.ports.out.grabacionv90;

import com.renaser.os.shared.domain.UserId;

/**
 * Encola el procesamiento asincrono de una validacion V90 (patron 202 + polling, CLAUDE.MD
 * §7 — "no bloquear el hilo de request esperando a Gemini"). El adaptador de infraestructura
 * lo ejecuta con {@code @Async} sobre un hilo virtual separado ({@code spring.threads.virtual.enabled}),
 * y en ese hilo vuelve a invocar {@code ProcesarValidacionV90UseCase} — separar el puerto
 * (out, disparo) del caso de uso (in, trabajo real) evita el problema clasico de auto-invocacion
 * con {@code @Async} (una llamada `this.metodo()` no pasa por el proxy de Spring).
 */
public interface DespacharValidacionV90Port {

    void despachar(UserId usuarioId, long grabacionId);
}
