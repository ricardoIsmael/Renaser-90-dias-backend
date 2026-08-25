package com.renaser.os.users.domain.model.mentorprofile;

/**
 * Semaforo operativo del mentor (tipo `estado_operativo` de docs/db/sql/BD_NUEVA_V1.sql).
 * El default de un mentor nuevo es GREEN.
 */
public enum MentorOperationalStatus {
    GREEN,
    YELLOW,
    RED
}
