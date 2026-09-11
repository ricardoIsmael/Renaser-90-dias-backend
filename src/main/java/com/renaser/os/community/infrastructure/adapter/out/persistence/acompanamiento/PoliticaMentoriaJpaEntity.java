package com.renaser.os.community.infrastructure.adapter.out.persistence.acompanamiento;

import com.renaser.os.community.domain.model.acompanamiento.CadenciaRotacion;
import jakarta.persistence.Column;
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

    /**
     * La sella la BASE ({@code DEFAULT now()}, V45). Ver el javadoc gemelo en
     * {@code AsignacionCelulaJpaEntity}: sin {@code insertable = false}, Hibernate manda el
     * {@code null} del mapper y el DEFAULT no se aplica, con lo que falla TODA escritura.
     */
    @Column(insertable = false, updatable = false)
    private Instant creadoEn;

    /**
     * La creacion la sella la base; la actualizacion la sella el adaptador con el reloj de la
     * aplicacion. {@code insertable = false} para que el INSERT use el DEFAULT, pero NO
     * {@code updatable = false}: si no, esta columna se quedaria para siempre en la hora del alta
     * y estaria mintiendo sobre lo unico que promete su nombre.
     *
     * <p>Este proyecto no tiene triggers (decision explicita), asi que nadie mas la va a tocar.
     */
    @Column(insertable = false)
    private Instant actualizadoEn;
}
