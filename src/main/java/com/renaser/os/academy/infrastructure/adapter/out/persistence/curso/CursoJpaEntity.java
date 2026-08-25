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

/** Tabla {@code cursos} (V1__baseline_renaser.sql:953-966). PK natural = id de Skool (text). */
@Entity
@Table(name = "cursos", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CursoJpaEntity {

    @Id
    private String id;

    private String slug;

    private String titulo;

    private String descripcion;

    private String portadaRuta;

    private Short orden;

    private boolean publicado;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private AccesoCursoJpa acceso;

    private String origen;

    private Short diaDesbloqueo;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
