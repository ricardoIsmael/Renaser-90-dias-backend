package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.estado;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "estado_onboarding", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EstadoOnboardingJpaEntity {

    @Id
    private UUID usuarioId;

    private String flujoActual;

    private String seccionActual;

    private Short pasoActual;

    /** JUSTIFICADO jsonb: estado de reanudacion de UI, opaco (baseline). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String progresoFlujo;

    private Instant terminosAceptadosEn;

    private Instant pactoAceptadoEn;

    private Instant pactoFirmadoEn;

    private Instant rocasSyncAceptadoEn;

    private Instant iniciadoEn;

    private Instant ultimaActividadEn;

    private boolean completado;

    private Instant completadoEn;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
