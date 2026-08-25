package com.renaser.os.academy.domain.model.curso;

/**
 * Espejo del tipo Postgres {@code acceso_curso} (V1__baseline_renaser.sql
 * linea 97). Gobierna, junto con {@code publicado}, si el catalogo expone el
 * curso — ver {@link Curso#visibleEnCatalogoPara}.
 */
public enum AccesoCurso {
    ABIERTO,
    RESTRINGIDO
}
