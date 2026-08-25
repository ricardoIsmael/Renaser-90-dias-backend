package com.renaser.os.community.infrastructure.adapter.out.persistence.cohorte;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "cohortes", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CohorteJpaEntity {

    @Id
    private UUID id;

    private String nombre;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EstadoCohorteJpa estado;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
