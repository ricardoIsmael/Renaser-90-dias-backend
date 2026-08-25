package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.cuestionario;

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

import java.time.Instant;

@Entity
@Table(name = "preguntas_onboarding", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreguntaOnboardingJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Short seccionId;

    private String clavePregunta;

    private String texto;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TipoPreguntaOnboardingJpa tipo;

    /** JUSTIFICADO jsonb: rangos/etiquetas de escala, opaco (baseline). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String configEscala;

    private boolean requerida;

    private Short orden;

    /** JUSTIFICADO jsonb: DSL del motor de formularios del cliente, opaco (baseline). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String reglasValidacion;

    private Integer preguntaPadreId;

    private Instant creadoEn;
}
