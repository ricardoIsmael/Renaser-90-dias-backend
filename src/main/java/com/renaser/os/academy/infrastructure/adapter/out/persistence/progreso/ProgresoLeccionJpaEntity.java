package com.renaser.os.academy.infrastructure.adapter.out.persistence.progreso;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Tabla {@code progreso_lecciones} (V1__baseline_renaser.sql:1046-1053). */
@Entity
@Table(name = "progreso_lecciones", schema = "renaser")
@IdClass(ProgresoLeccionId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProgresoLeccionJpaEntity {

    @Id
    private UUID usuarioId;

    @Id
    private String leccionId;

    private Instant completadaEn;
}
