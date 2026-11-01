package com.renaser.os.habits.infrastructure.adapter.in.rest.categoria;

import com.renaser.os.habits.domain.model.categoria.CategoriaHabito;
import com.renaser.os.habits.domain.model.categoria.CodigoDeCategoria;

/**
 * Una categoria (dimension de Training) para quien hace el programa. {@code key} es la clave de la base y {@code code} lo que lleva el campo
 * {@code category} de los habitos (el ingles de siempre en las cuatro de la Escuela, la clave en las demas): la app une un habito con su
 * categoria por {@code code}. Proyeccion a mano, nunca el agregado.
 */
public record CategoriaDeHabitoResponse(String key, String code, String name, String icon, int order) {

    public static CategoriaDeHabitoResponse from(CategoriaHabito c) {
        return new CategoriaDeHabitoResponse(c.clave(), CodigoDeCategoria.deClave(c.clave()), c.nombre(), c.icono(), c.orden());
    }
}
