package com.renaser.os.notifications.domain.model.semaforo;

/**
 * Qué pasó en la semana del semáforo, dicho sin cifras ni colores (D-168). Elige el texto de cada
 * aviso y, cuando exista, la plantilla HTML con la que se mande (pedido del dueño, 2026-09-25): la
 * plantilla elige su diseño por este caso y los textos de {@link RedaccionDelSemaforo} quedan de
 * respaldo y para el push.
 */
public enum CasoDelAviso {

    /** La persona tuvo hábitos u objetivos programados en la semana: el resultado se ve adentro. */
    CON_REGISTROS,
    /** La persona no tuvo nada programado, o nadie del grupo (o de los grupos) tuvo registros. */
    SIN_REGISTROS,
    /** Hay aprendices que quedaron en amarillo o en rojo. */
    NECESITAN_APOYO,
    /** Todos tuvieron registros y ninguno quedó en amarillo ni en rojo. */
    SIN_ALERTAS,
    /** Cualquier otra combinación, o datos que no cierran: el texto de siempre. */
    NEUTRO
}
