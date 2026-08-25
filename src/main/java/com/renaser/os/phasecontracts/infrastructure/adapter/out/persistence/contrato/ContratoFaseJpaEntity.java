package com.renaser.os.phasecontracts.infrastructure.adapter.out.persistence.contrato;

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
@Table(name = "contratos_fase", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContratoFaseJpaEntity {

    @Id
    private UUID id;

    private UUID participanteId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private FaseProgramaJpa fase;

    private String bucket;

    private String rutaFirma;

    private Instant firmadoEn;

    private Instant creadoEn;
}
