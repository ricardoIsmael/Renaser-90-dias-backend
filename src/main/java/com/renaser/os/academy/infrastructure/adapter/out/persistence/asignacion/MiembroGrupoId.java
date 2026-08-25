package com.renaser.os.academy.infrastructure.adapter.out.persistence.asignacion;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/** Clase de PK compuesta para {@link MiembroGrupoJpaEntity} (grupo_id, usuario_id). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MiembroGrupoId implements Serializable {

    private Long grupoId;
    private UUID usuarioId;
}
