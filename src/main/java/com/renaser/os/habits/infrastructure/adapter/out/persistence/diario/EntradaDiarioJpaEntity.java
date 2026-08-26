package com.renaser.os.habits.infrastructure.adapter.out.persistence.diario;

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

/** Tabla `entradas_diario` (baseline linea 369) — UNIQUE (participante_id, fecha, tipo). */
@Entity
@Table(name = "entradas_diario", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntradaDiarioJpaEntity {

    @Id
    private UUID id;

    private UUID participanteId;

    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TipoEntradaDiarioJpa tipo;

    private String contenidoTexto;

    private String audioBucket;

    private String audioRuta;

    private String transcripcion;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
