package com.renaser.os.community.infrastructure.adapter.out.persistence.acompanamiento;

import com.renaser.os.community.domain.model.acompanamiento.CadenciaRotacion;
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

/** Fila de `politicas_mentoria` (V45). 1:1 con la cohorte. */
@Entity
@Table(name = "politicas_mentoria", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PoliticaMentoriaJpaEntity {

    @Id
    private UUID cohorteId;

    private short capacidadCelula;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private CadenciaRotacion cadenciaRotacion;

    private String zonaHoraria;

    private short diaTraslado;

    private short diasSinActividadAlerta;

    private UUID celulaRecepcionId;

    private int version;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
