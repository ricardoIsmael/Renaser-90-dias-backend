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

    private Instant creadoEn;
}
