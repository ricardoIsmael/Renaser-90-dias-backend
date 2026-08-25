package com.renaser.os.calendar.infrastructure.adapter.out.persistence.evento;

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

/** 0..1 por evento — {@code evento_id} ES la PK (no un id propio), ver baseline. */
@Entity
@Table(name = "recurrencias_evento", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecurrenciaJpaEntity {

    @Id
    private UUID eventoId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private FrecuenciaRecurrenciaJpa frecuencia;

    private Short intervalo;

    private Instant hasta;

    private Short repeticiones;
}
