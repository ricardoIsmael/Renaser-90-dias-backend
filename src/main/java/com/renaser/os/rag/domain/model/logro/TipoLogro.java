package com.renaser.os.rag.domain.model.logro;

/**
 * Los logros que el acompanante puede celebrar en el chat. Cada uno es el espejo de un evento
 * publico de otro modulo; el nombre del valor es el que se escribe en
 * {@code renaser.ia.acompanante.logros-en-chat-tipos}.
 *
 * <p>Solo hitos reales, no cada accion: {@code HabitoCompletadoEvent} queda afuera a proposito
 * (varias veces por dia, muchas veces marcado desde el propio chat — celebrarlo seria ruido).
 */
public enum TipoLogro {

    /** {@code habits.api.RachaCompletadaEvent}: un ciclo completo de 24h del Dia sin celular. */
    RACHA_SIN_CELULAR,

    /** {@code rocks.api.RocaCompletadaEvent}: una Roca Diaria completada con su evidencia. */
    ROCA_COMPLETADA
}
