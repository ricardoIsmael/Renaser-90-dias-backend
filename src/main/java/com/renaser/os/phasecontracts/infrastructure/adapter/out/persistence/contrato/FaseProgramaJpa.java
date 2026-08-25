package com.renaser.os.phasecontracts.infrastructure.adapter.out.persistence.contrato;

/**
 * Espejo del tipo Postgres `fase_programa`. Los nombres coinciden 1:1 con
 * FasePrograma (domain), pero se mantiene un tipo local igual que en `users`
 * (RolUsuarioJpa, EstadoSolicitudJpa...): la entidad JPA nunca usa un tipo de
 * dominio directo, siempre traduce vía el mapper — asi si un dia el nombre en la
 * base diverge del nombre de dominio, el unico lugar que cambia es el mapper.
 */
public enum FaseProgramaJpa {
    FASE_1_RENACER,
    FASE_2_DESARROLLO,
    FASE_3_GUERRERO_ALQUIMISTA,
    FASE_4_ASCENSION
}
