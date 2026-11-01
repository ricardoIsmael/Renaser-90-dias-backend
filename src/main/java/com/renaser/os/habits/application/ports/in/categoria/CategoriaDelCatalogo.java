package com.renaser.os.habits.application.ports.in.categoria;

import com.renaser.os.habits.domain.model.categoria.CategoriaHabito;

/**
 * Una categoria como la ve Administracion: ella y cuantos habitos del catalogo tiene. {@code puedeEliminarse} dice si hoy se podria
 * borrar (sin ningun habito); {@code puedeDesactivarse}, si no tiene habitos activos.
 */
public record CategoriaDelCatalogo(CategoriaHabito categoria, int habitosActivos, int habitosTotales) {

    public boolean puedeEliminarse() {
        return habitosTotales == 0;
    }

    public boolean puedeDesactivarse() {
        return habitosActivos == 0;
    }
}
