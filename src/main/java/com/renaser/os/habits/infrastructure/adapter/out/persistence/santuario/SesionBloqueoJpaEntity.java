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
@Table(name = "sesiones_bloqueo", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SesionBloqueoJpaEntity {

    @Id
    private UUID registroHabitoId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EstadoSesionBloqueoJpa estado;

    private Instant iniciadaEn;

    private Instant terminadaEn;

    private Short duracionMinimaMin;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private MotivoSalidaBloqueoJpa motivoSalida;

    private String evidenciaSalidaBucket;

    private String evidenciaSalidaRuta;

    private boolean penalizacionAplicada;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
