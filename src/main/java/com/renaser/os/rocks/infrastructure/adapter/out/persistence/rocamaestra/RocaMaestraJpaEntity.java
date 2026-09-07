package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocamaestra;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rocas_maestras", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RocaMaestraJpaEntity {

    @Id
    private UUID id;

    private UUID participanteId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EjeObjetivoJpa eje;

    private String objetivo;

    /** Meta / avance / unidad de V35. Las tres van juntas o las tres en null — lo impone el CHECK. */
    private BigDecimal meta;

    private BigDecimal avance;

    private String unidad;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
