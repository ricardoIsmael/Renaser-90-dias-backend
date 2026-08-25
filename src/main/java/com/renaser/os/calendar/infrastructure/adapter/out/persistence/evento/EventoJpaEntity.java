package com.renaser.os.calendar.infrastructure.adapter.out.persistence.evento;

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
@Table(name = "eventos", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventoJpaEntity {

    @Id
    private UUID id;

    private String titulo;

    private String descripcion;

    private String portadaRuta;

    private Instant iniciaEn;

    private Integer duracionMinutos;

    private String timezone;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TipoUbicacionJpa tipoUbicacion;

    private String valorUbicacion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TipoAudienciaJpa tipoAudiencia;

    private Short nivelMinimoId;

    private String cursoId;

    private UUID celulaDestinoId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EstadoEventoJpa estado;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TipoEventoJpa tipoEvento;

    private boolean notificarAlCrear;

    private boolean recordarPorEmail;

    private boolean recordatoriosPersonalizados;

    private UUID creadoPor;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
