package com.renaser.os.points.infrastructure.adapter.out.persistence.ranking;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ranking_aprendices", schema = "renaser")
@IdClass(RankingAprendizId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RankingAprendizJpaEntity {

    @Id
    private LocalDate fecha;

    @Id
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TipoRankingJpa tipo;

    @Id
    private UUID participanteId;

    private Integer posicion;

    private BigDecimal puntaje;
}
