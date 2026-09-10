package com.renaser.os.users.infrastructure.adapter.out.persistence.mentorprofile;

/**
 * Espejo del tipo Postgres `especialidad_mentor` (V48). Los nombres coinciden con
 * {@code EspecialidadMentor}, pero se mantiene un tipo local igual para ser consistente con los
 * otros enums de este paquete — la entidad JPA no importa tipos de dominio, siempre traduce via el
 * mapper.
 */
public enum EspecialidadMentorJpa {
    NEGOCIO,
    MENTE,
    RELACIONES
}
