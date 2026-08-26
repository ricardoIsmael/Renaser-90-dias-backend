package com.renaser.os.habits.infrastructure.adapter.out.persistence.habito;

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

/**
 * Mapeo PARCIAL de `habitos` (CLAUDE.MD §5.4.1/§5.4.5) — no incluye `grupo`,
 * `tipo_entrada_diario` ni `orden`: columnas nullable o con DEFAULT de Postgres
 * que este agregado no modela todavia (ver docs/MODULO_HABITS.md "que quedo
 * simplificado"). Omitirlas del mapeo deja que la columna tome su DEFAULT/NULL
 * en el INSERT — no rompe el esquema, pero cualquier valor previo en esas
 * columnas se pierde en un UPDATE via este adaptador (riesgo aceptado y
 * documentado, sin flujo de escritura que las use hoy). {@code dia_limite_edicion_libre}
 * SI se mapea desde 2026-08-26 (hueco #12, EditarPreferenciaHorarioUseCase).
 */
@Entity
@Table(name = "habitos", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HabitoJpaEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private AmbitoHabitoJpa ambito;

    private UUID participanteId;

    private String titulo;

    private String descripcion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TipoHabitoJpa tipo;

    private String categoriaClave;

    private String iconoClave;

    private String claveSistema;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ExigenciaEvidenciaJpa exigenciaEvidencia;

    private boolean esOpcional;

    private boolean obligatorioEnIntoxicacion;

    private boolean eleccionDiaSemanal;

    private Short horasExtraEvidencia;

    private Short diaLimiteEdicionLibre;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private PlantillaHabitoPersonalJpa plantillaClave;

    private String etiquetaMeta;

    private boolean activo;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
