package com.renaser.os.habits.application.ports.out.categoria;

import com.renaser.os.habits.domain.model.categoria.CategoriaHabito;
import com.renaser.os.shared.domain.Formacion;

import java.util.List;
import java.util.Optional;

/** Lectura de las categorias de habito de cada formacion (spec 023). */
public interface LoadCategoriaHabitoPort {

    Optional<CategoriaHabito> byClave(String clave);

    /** Las de una formacion, activas e inactivas, en su orden (panel de Administracion). */
    List<CategoriaHabito> deFormacion(Formacion formacion);

    /** Solo las activas de una formacion, en su orden: las dimensiones que ve quien hace el programa. */
    List<CategoriaHabito> activasDe(Formacion formacion);

    boolean existeClave(String clave);

    /** Si otra categoria de la misma formacion ya se llama asi (sin distinguir mayusculas ni tildes); {@code excluyendo} es la que se edita. */
    boolean existeNombreEn(Formacion formacion, String nombre, String excluyendo);

    /** Cuantos habitos del catalogo (activos y en total) tiene la categoria. */
    ConteoDeHabitos contarHabitos(String clave);

    /** El siguiente orden libre de la formacion (para dejar una categoria nueva al final). */
    int siguienteOrden(Formacion formacion);

    record ConteoDeHabitos(int activos, int total) {
    }
}
