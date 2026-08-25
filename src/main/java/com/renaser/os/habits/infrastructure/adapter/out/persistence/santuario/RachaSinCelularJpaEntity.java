package com.renaser.os.habits.infrastructure.adapter.out.persistence.santuario;

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
@Table(name = "rachas_sin_celular", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RachaSinCelularJpaEntity {

    @Id
    private UUID id;

    private UUID registroHabitoId;

    private UUID participanteId;

    private Instant iniciadaEn;

    private Instant terminadaEn;

    private Short horasObjetivo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EstadoRachaJpa estado;

    private Integer duracionMinutos;

    private String motivoRuptura;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
