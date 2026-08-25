package com.renaser.os.users.infrastructure.adapter.out.persistence.mentorprofile;

/**
 * Espejo del tipo Postgres `nivel_mentor`. Los nombres coinciden con MentorLevel (N0-N3),
 * pero se mantiene un tipo local igual para ser consistente con los otros 3 enums de este
 * paquete — la entidad JPA no importa tipos de dominio, siempre traduce vía el mapper.
 */
public enum NivelMentorJpa {
    N0,
    N1,
    N2,
    N3
}
