package com.renaser.os.rag.domain.model.semaforo;

/**
 * El color con el que una persona cerró su semana del semáforo, visto desde el chat del acompañante.
 * Espejo de {@code points.api.ColorSemaforo}: el puerto de entrada de {@code rag} no expone tipos de
 * otro módulo (mismo criterio que {@code TipoLogro} y que el {@code tipoAviso} de
 * {@code AvisoHabitoEnChatCommand}).
 *
 * <p>La palabra que acompaña al color («Al día», «Requiere atención»…) NO se copia acá: viaja en el
 * comando, y su único dueño sigue siendo {@code points}. El nombre de cada valor es el mismo que en
 * {@code ColorSemaforo}, y el listener los traduce con un {@code switch} exhaustivo: si {@code points}
 * agrega un color, deja de compilar en vez de fallar en el outbox.
 */
public enum ColorDeLaSemana {
    VERDE,
    AMARILLO,
    ROJO,
    SIN_DATOS
}
