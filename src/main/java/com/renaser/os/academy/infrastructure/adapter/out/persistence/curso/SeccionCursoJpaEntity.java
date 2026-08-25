package com.renaser.os.academy.infrastructure.adapter.out.persistence.curso;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Tabla {@code secciones_curso} (V1__baseline_renaser.sql:977-985). */
@Entity
@Table(name = "secciones_curso", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeccionCursoJpaEntity {

    @Id
    private String id;

    private String cursoId;

    private String titulo;

    private Short orden;

    private Short diaDesbloqueo;
}
