package com.renaser.os.users.infrastructure.adapter.in.rest.admin;

import com.renaser.os.users.api.FormacionConfigurada;

import java.util.List;

/** Lo que Administración configura de una formación (spec 022). {@code version} es la que hay que devolver al guardar. */
public record FormacionResponse(String codigo, String nombre, String descripcion, boolean activa, List<Integer> duracionesDias,
                                List<String> etiquetasDeDuracion, int version) {

    public static FormacionResponse from(FormacionConfigurada f) {
        return new FormacionResponse(f.formacion().codigo(), f.nombre(), f.descripcion(), f.activa(), f.duracionesDias(),
                f.etiquetasDeDuracion(), f.version());
    }
}
