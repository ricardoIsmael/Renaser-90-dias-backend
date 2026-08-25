package com.renaser.os.academy.infrastructure.adapter.out.persistence.recomendacion;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Tabla {@code recomendaciones_academia} (V1__baseline_renaser.sql:1054-1065). */
@Entity
@Table(name = "recomendaciones_academia", schema = "renaser")
@IdClass(RecomendacionAcademiaId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecomendacionAcademiaJpaEntity {

    @Id
    private UUID participanteId;

    @Id
    private LocalDate fecha;

    private String leccionId;

    private String motivo;

    private Instant creadoEn;
}
