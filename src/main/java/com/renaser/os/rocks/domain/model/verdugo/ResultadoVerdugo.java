package com.renaser.os.rocks.domain.model.verdugo;

/**
 * Espejo del tipo Postgres `resultado_verdugo`. {@code IGNORADO} nunca lo
 * manda el cliente — lo asigna el barrido de las 23:55 sobre eventos sin
 * resolver (ver `VerdugoIgnoradoScheduler`).
 */
public enum ResultadoVerdugo {
    COMPLETADO,
    POSTERGADO,
    POSPUESTO_30,
    IGNORADO
}
