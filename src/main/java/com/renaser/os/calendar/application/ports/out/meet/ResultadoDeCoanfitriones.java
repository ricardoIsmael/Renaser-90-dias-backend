package com.renaser.os.calendar.application.ports.out.meet;

/**
 * Lo que quedó de pedirle a Google que unas personas sean coanfitrionas de una sala (spec 040 FR-038). Solo cantidades: nunca correos.
 *
 * @param pedidos    a cuántas personas se les pidió ser coanfitrionas
 * @param quedaron   cuántas lo son ahora (las que ya lo eran cuentan, y no se les pide de nuevo)
 * @param codigoHttp el código con que Google rechazó a la última que no quedó, o {@code null} si no hubo rechazo
 */
public record ResultadoDeCoanfitriones(int pedidos, int quedaron, Integer codigoHttp) {
}
