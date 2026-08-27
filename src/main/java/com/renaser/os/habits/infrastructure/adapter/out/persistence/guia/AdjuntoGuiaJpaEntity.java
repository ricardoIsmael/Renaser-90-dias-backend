package com.renaser.os.habits.infrastructure.adapter.out.persistence.guia;

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
@Table(name = "adjuntos_guia", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdjuntoGuiaJpaEntity {

    @Id
    private UUID id;

    private UUID guiaId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private SeccionGuiaJpa seccion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TipoMedioGuiaJpa tipoMedio;

    private String url;

    private String rutaStorage;

    private String mime;

    private Integer tamanoBytes;

    private String nombreOriginal;

    private String titulo;

    private short orden;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
