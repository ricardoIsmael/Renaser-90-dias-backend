package com.renaser.os.academy.infrastructure.adapter.out.persistence.progreso;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/** Clase de PK compuesta para {@link ProgresoLeccionJpaEntity} (usuario_id, leccion_id). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProgresoLeccionId implements Serializable {

    private UUID usuarioId;
    private String leccionId;
}
