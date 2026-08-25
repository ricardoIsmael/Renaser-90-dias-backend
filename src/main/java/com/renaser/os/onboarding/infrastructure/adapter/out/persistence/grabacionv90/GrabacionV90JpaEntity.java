package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.grabacionv90;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "grabaciones_v90", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GrabacionV90JpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID usuarioId;

    /** texto libre en la base, no un enum Postgres (baseline). */
    private String fase;

    private String eje;

    private Short indice;

    private String clavePregunta;

    private boolean grabada;

    private Long mediaId;

    private BigDecimal duracionSegundos;

    private String transcripcion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EstadoIAv90Jpa estadoIa;

    private Short intentosIa;

    /** JUSTIFICADO jsonb: salida semiestructurada del modelo, opaco (baseline). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String feedbackIa;

    private Instant grabadaEn;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
