package com.renaser.os.users.infrastructure.adapter.out.persistence.user;

/**
 * Espejo EXACTO del tipo Postgres `rol_usuario` (español). Existe solo porque el dominio
 * usa UserRole en ingles y la base en español (D-21) — la traduccion explicita vive en
 * UserPersistenceMapper, no en el dominio ni en JPA magico por nombre.
 */
public enum RolUsuarioJpa {
    APRENDIZ,
    MENTOR,
    LIDER_MENTORES,
    ADMIN,
    ALQUIMISTA
}
