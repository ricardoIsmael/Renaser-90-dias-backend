package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.habitos.ConsultarCategoriasDeHabitoPort;
import com.renaser.os.rag.domain.model.habitopersonal.CategoriaDelHabito;

import java.util.List;

/**
 * Las categorias de habito de cada formacion para las pruebas de SER (spec 038). La Escuela tiene las cuatro de siempre; el Diplomado y la
 * Certificacion tienen las SUYAS (Administracion las crea) y ninguna se llama como las de la Escuela.
 */
final class CategoriasDePrueba {

    static final List<CategoriaDelHabito> LAS_DE_LA_ESCUELA = List.of(
            new CategoriaDelHabito("CUERPO", "Cuerpo"), new CategoriaDelHabito("MENTE", "Mente"),
            new CategoriaDelHabito("CONSCIENCIA", "Emociones"), new CategoriaDelHabito("ESPIRITU", "Espíritu"));

    static final List<CategoriaDelHabito> LAS_DEL_DIPLOMADO = List.of(
            new CategoriaDelHabito("DIPLOMADO_ESTUDIO_PROFUNDO", "Estudio profundo"),
            new CategoriaDelHabito("DIPLOMADO_PRACTICA_CLINICA", "Práctica clínica"));

    static final List<CategoriaDelHabito> LAS_DE_LA_CERTIFICACION = List.of(
            new CategoriaDelHabito("CERTIFICACION_MENTORIA", "Mentoría"));

    /** Un puerto que siempre contesta con las categorias dadas. */
    static ConsultarCategoriasDeHabitoPort de(List<CategoriaDelHabito> categorias) {
        return persona -> categorias;
    }

    static final ConsultarCategoriasDeHabitoPort ESCUELA = de(LAS_DE_LA_ESCUELA);

    private CategoriasDePrueba() {
    }
}
