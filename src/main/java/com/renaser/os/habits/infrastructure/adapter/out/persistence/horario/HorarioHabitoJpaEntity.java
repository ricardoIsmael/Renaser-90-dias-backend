package com.renaser.os.habits.infrastructure.adapter.out.persistence.horario;

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
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "horarios_habito", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HorarioHabitoJpaEntity {

    @Id
    private UUID id;

    private UUID habitoId;

    private Short diaInicio;

    private Short diaFin;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TipoDiaJpa tipoDia;

    private LocalTime horaDisparo;

    private LocalTime horaLimite;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
