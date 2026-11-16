package com.renaser.os.calendar.domain.model.meet;

/**
 * Cómo quedó, en una sala de Meet, el pedido de hacer coanfitrión a quien dirige la sesión (spec 040 FR-038, decisión del dueño del 14/11/2026):
 * Google solo graba y transcribe solo cuando entra alguien «con permiso para grabar» —el anfitrión o un coanfitrión—. {@code null} en la fila
 * significa que todavía no se pidió.
 */
public enum EstadoDeCoanfitriones {
    /** Todos los pedidos quedaron como coanfitriones (los que ya lo eran cuentan). */
    OK,
    /** Algunos quedaron y otros no (p. ej. Google rechazó el correo de una persona de fuera de la organización). */
    PARCIAL,
    /** Google no dejó agregar a nadie (falta el permiso, la sala no es de la app, el correo no es de una cuenta de Google…). */
    FALLIDO,
    /** No se encontró un correo de quien dirige la sesión: no hay a quién pedirle. */
    SIN_CORREO
}
