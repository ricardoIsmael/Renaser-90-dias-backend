package com.renaser.os.users.infrastructure.adapter.out.persistence.user;

/** Espejo del tipo Postgres `estado_usuario`. INACTIVO no tiene equivalente en UserStatus todavia (R-3). */
public enum EstadoUsuarioJpa {
    ACTIVO,
    INACTIVO,
    SUSPENDIDO
}
