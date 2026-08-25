package com.renaser.os.academy.infrastructure.adapter.out.persistence.curso;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Tabla {@code recursos_leccion} (V1__baseline_renaser.sql:1005-1013). */
@Entity
@Table(name = "recursos_leccion", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecursoLeccionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String leccionId;

    private String nombre;

    private String url;

    private Short orden;
}
