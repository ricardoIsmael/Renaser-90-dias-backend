package com.renaser.os.community.domain.model.acompanamiento;

/** Por qué se abrió o cerró un intervalo. Sin esto el historial no se puede auditar. */
public enum MotivoAsignacion {

    /** Alta o baja hecha por un administrador desde las rutas existentes de admin. */
    ADMINISTRATIVO,

    /** Entrada a la recepción de la cohorte al activarse el programa. */
    RECEPCION,

    /** Paso de recepción a grupo estable a partir del día configurado (P-01). */
    TRASLADO,

    /** Cambio periódico de mentor conservando grupo, alumnos y chat (D-06). */
    ROTACION,

    /** Reconciliación de la migración: el intervalo arranca en la fecha de corte verificable. */
    MIGRACION
}
