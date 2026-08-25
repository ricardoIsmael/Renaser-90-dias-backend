package com.renaser.os.community.domain.model.publicacion;

/**
 * Espejo del tipo Postgres `tipo_reaccion` (V1__baseline_renaser.sql:74): ME_GUSTA/NO_ME_GUSTA
 * en dominio y base (D-36); LIKE/DISLIKE solo en la frontera REST.
 */
public enum TipoReaccion {
    ME_GUSTA,
    NO_ME_GUSTA
}
