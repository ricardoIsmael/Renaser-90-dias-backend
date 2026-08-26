package com.renaser.os.habits.infrastructure.adapter.out.persistence.espiritu;

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
@Table(name = "registros_espiritu", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistroEspirituJpaEntity {

    @Id
    private UUID id;

    private UUID participanteId;

    private Short dia;

    private Instant desbloqueadoEn;

    private Instant fechaLimite;

    private Instant entregadoEn;

    private String resumenTexto;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EstadoRegistroEspirituJpa estado;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
