package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocadiaria;

import com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocamaestra.EjeObjetivoJpa;
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
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "rocas_diarias", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RocaDiariaJpaEntity {

    @Id
    private UUID id;

    private UUID participanteId;

    private LocalDate fecha;

    private Short posicion;

    private String titulo;

    private String descripcion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ColorParetoJpa color;

    private Short puntajeImpacto;

    private boolean esDelegable;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EjeObjetivoJpa eje;

    private UUID rocaSemanalId;

    private LocalTime horaInicio;

    private LocalTime horaFin;

    private boolean completada;

    private Instant completadaEn;

    private Short puntosOtorgados;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
