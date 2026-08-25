package com.renaser.os.academy.infrastructure.adapter.out.persistence.curso;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Clase de PK compuesta para {@link RolPermitidoCursoJpaEntity} (curso_id, rol_id). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolPermitidoCursoId implements Serializable {

    private String cursoId;
    private Short rolId;
}
