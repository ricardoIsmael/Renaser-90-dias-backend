package com.renaser.os.users.infrastructure.adapter.out.persistence.participante;

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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tabla `participantes_programa` (baseline V1, ~linea 255). Mapea solo las columnas que
 * este agregado usa hoy — igual criterio que UserJpaEntity: {@code habitos_escalonados_en}
 * queda fuera a proposito (nullable/DEFAULT en Postgres, no hace falta que Hibernate lo
 * conozca para un INSERT valido). {@code tipo_meta}, {@code nombre_reto_personal} y
 * {@code programa_completado_en} SI se mapean desde 2026-08-26 (hueco #1 de
 * docs/PLAN_INTEGRACION_FRONTEND.md — el frontend real espera estos 3 campos dentro de
 * `traineeProfile`). {@code dia_programa_avanzado_el} SI se mapea desde D-66
 * (`AvanzarDiaProgramaScheduler` ya existe y lo necesita para la idempotencia del cron).
 *
 * <p>{@code fecha_graduacion_esperada} NO esta mapeada: es {@code GENERATED ALWAYS AS
 * (fecha_inicio + 90) STORED} — Postgres RECHAZA cualquier INSERT/UPDATE que la
 * mencione. El dominio la calcula al vuelo (ver {@code ParticipacionPrograma.fechaGraduacionEsperada()}).
 */
@Entity
@Table(name = "participantes_programa", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParticipacionProgramaJpaEntity {

    @Id
    private UUID usuarioId;

    private UUID mentorId;

    private UUID celulaId;

    private short diaPrograma;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private FaseProgramaJpa fase;

    private LocalDate fechaInicio;

    private Instant programaActivadoEn;

    private String timezone;

    private boolean programaCompletado;

    private short diaPostPrograma;

    private Instant creadoEn;

    private Instant actualizadoEn;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TipoMetaJpa tipoMeta;

    private String nombreRetoPersonal;

    private Instant programaCompletadoEn;

    /** `dia_programa_avanzado_el` — SI se mapea desde D-66: es la fecha local de la
     * ultima sincronizacion del reloj (`AvanzarDiaProgramaScheduler`). */
    private LocalDate diaProgramaAvanzadoEl;

    /** `dias_ajuste_programa` (V20) — dias de calendario que no cuentan para el programa.
     * Con signo: positivo retrocede al participante, negativo lo adelanta. */
    private short diasAjustePrograma;
}
