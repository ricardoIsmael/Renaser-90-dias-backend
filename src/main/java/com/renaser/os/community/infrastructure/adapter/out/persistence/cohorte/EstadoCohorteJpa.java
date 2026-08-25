package com.renaser.os.community.infrastructure.adapter.out.persistence.cohorte;

/** Espejo del tipo Postgres `estado_cohorte` (V1__baseline_renaser.sql:50). La entidad
 * JPA nunca usa el enum de dominio directo — siempre traduce via el mapper (CLAUDE.MD
 * sec. "FaseProgramaJpa"). */
public enum EstadoCohorteJpa {
    PLANIFICADA,
    ACTIVA,
    COMPLETADA
}
