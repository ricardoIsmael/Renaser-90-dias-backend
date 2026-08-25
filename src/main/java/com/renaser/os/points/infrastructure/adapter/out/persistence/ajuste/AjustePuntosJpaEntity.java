package com.renaser.os.points.infrastructure.adapter.out.persistence.ajuste;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ajustes_puntos_liga", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AjustePuntosJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID participanteId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private MotivoPuntosJpa motivo;

    private Short delta;

    private Short deltaAplicado;

    private Integer saldoPosterior;

    private String nota;

    private Instant creadoEn;
}
