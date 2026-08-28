package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.respuesta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** {@code valorTexto}/{@code valorJson} son respuestas de onboarding del aprendiz (CLAUDE.md
 * §5.4.9: nunca loguear contenido de evidencia/respuestas de onboarding) — excluidos del
 * {@code toString()} que {@code @Data} generaria por default. */
@Entity
@Table(name = "respuestas_onboarding", schema = "renaser")
@Data
@ToString(exclude = {"valorTexto", "valorJson"})
@NoArgsConstructor
@AllArgsConstructor
public class RespuestaOnboardingJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID usuarioId;

    private Integer preguntaId;

    private String valorTexto;

    private BigDecimal valorNumero;

    private Boolean valorBooleano;

    private Short valorEscala;

    /** JUSTIFICADO jsonb: valor de SELECCION_MULTIPLE u otro valor estructurado, opaco (baseline). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String valorJson;

    private Long mediaId;

    private Instant aceptadaEn;

    private Instant respondidaEn;

    private Instant actualizadoEn;
}
