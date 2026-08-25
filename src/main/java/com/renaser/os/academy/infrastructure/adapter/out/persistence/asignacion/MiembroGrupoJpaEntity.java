package com.renaser.os.academy.infrastructure.adapter.out.persistence.asignacion;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Tabla {@code miembros_grupo} (V1__baseline_renaser.sql:1020-1027). */
@Entity
@Table(name = "miembros_grupo", schema = "renaser")
@IdClass(MiembroGrupoId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MiembroGrupoJpaEntity {

    @Id
    private Long grupoId;

    @Id
    private UUID usuarioId;

    private Instant creadoEn;
}
