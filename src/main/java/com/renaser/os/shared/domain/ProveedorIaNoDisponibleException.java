package com.renaser.os.shared.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * El proveedor de IA (hoy Google GenAI) no puede atender AHORA: cuota agotada, saturacion, o un
 * fallo transitorio de su lado (timeout, 5xx). No es un error del aprendiz ni un bug nuestro, y
 * la respuesta correcta hacia afuera es "volve a intentar en un rato" — un 503 con
 * {@code Retry-After} — no un 500, que es lo que salia antes (auditoria NFR 2026-09-06).
 *
 * <p>Por que importa distinguirlo: un 500 le dice al cliente "esto esta roto, reintenta cuando
 * quieras", y el movil reintentaba de inmediato contra una cuota que no se iba a liberar en
 * segundos — cada reintento gastaba mas cuota y alargaba el problema. Con el 503 y el
 * {@code Retry-After} el cliente sabe cuanto esperar.
 *
 * <p>Vive en {@code shared/domain} porque la producen dos adaptadores distintos (el chat y los
 * embeddings) y la traduce a HTTP un solo lugar ({@code GlobalExceptionHandler}). Solo conoce
 * {@code java.time}: el dominio no importa nada del SDK del proveedor, y por eso el handler
 * tampoco tiene que conocerlo. Quien sabe que un 429 de Google significa "cuota" es el adaptador.
 */
public class ProveedorIaNoDisponibleException extends RuntimeException {

    private final Duration reintentarEn;

    public ProveedorIaNoDisponibleException(String message, Duration reintentarEn) {
        super(message);
        this.reintentarEn = Objects.requireNonNull(reintentarEn, "reintentarEn es obligatorio");
    }

    /** Cuanto conviene esperar antes de volver a intentar; va al header {@code Retry-After}. */
    public Duration reintentarEn() {
        return reintentarEn;
    }
}
