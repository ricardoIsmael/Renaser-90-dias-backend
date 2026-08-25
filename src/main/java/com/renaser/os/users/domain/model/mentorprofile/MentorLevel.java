package com.renaser.os.users.domain.model.mentorprofile;

/**
 * Nivel del mentor (tipo `nivel_mentor` de docs/db/sql/BD_NUEVA_V1.sql).
 * Orden ascendente de N0 a N3; el default de un mentor nuevo es N0.
 */
public enum MentorLevel {
    N0,
    N1,
    N2,
    N3
}
