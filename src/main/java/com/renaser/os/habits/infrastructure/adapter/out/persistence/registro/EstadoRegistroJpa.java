package com.renaser.os.habits.infrastructure.adapter.out.persistence.registro;

/** Espejo de `estado_registro` — separado del dominio a proposito (CLAUDE.MD §5.4.1/§5.4.5),
 * aunque los valores coincidan textualmente (mismo criterio que `EstadoTicketMentorJpa` en `support`). */
public enum EstadoRegistroJpa {
    PENDIENTE,
    EN_CURSO,
    COMPLETADO,
    FALLIDO,
    EXPIRADO
}
