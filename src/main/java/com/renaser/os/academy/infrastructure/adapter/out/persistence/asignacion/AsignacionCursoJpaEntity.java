package com.renaser.os.academy.infrastructure.adapter.out.persistence.asignacion;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Tabla {@code asignaciones_curso} (V1__baseline_renaser.sql:1028-1045). Arco exclusivo usuario⊕grupo. */
@Entity
@Table(name = "asignaciones_curso", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AsignacionCursoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String cursoId;

    private UUID usuarioId;

    private Long grupoId;

    private Instant desde;

    private Instant hasta;

    private Instant revocadaEn;

    private UUID asignadaPor;

    private Instant creadoEn;
}
