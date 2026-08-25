package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "medias_onboarding", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaOnboardingJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID usuarioId;

    private String flujo;

    private String clavePregunta;

    /** Texto libre en la base ("audio"|"firma"|"documento", baseline) — no un enum Postgres. */
    private String clase;

    private String bucket;

    private String rutaStorage;

    private String mime;

    private Long tamanoBytes;

    private BigDecimal duracionSegundos;

    /** JUSTIFICADO jsonb: metadatos libres del archivo subido, opaco (baseline). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadatos;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
