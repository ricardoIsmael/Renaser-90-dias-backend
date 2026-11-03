package com.renaser.os.habits.application.ports.out.categoria;

import com.renaser.os.habits.domain.model.categoria.CategoriaHabito;

/** Escritura de las categorias de habito (spec 023). */
public interface SaveCategoriaHabitoPort {

    CategoriaHabito save(CategoriaHabito categoria);

    /** Falla si algun habito la usa (llave RESTRICT): quien llama ya lo comprobo para dar un motivo claro. */
    void eliminar(String clave);
}
