package com.renaser.os.academy.infrastructure.adapter.out.persistence.curso;

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

/** Tabla {@code lecciones} (V1__baseline_renaser.sql:986-1004). */
@Entity
@Table(name = "lecciones", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeccionJpaEntity {

    @Id
    private String id;

    private String cursoId;

    private String seccionId;

    private String titulo;

    private Short orden;

    private String cuerpoHtml;

    private String cuerpoMd;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TipoVideoLeccionJpa videoTipo;

    private String videoUrl;

    private String videoMiniaturaUrl;

    private Long videoDuracionMs;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
