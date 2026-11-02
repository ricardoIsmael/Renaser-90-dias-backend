package com.renaser.os.rag.application.ports.out.habitos;

import com.renaser.os.rag.domain.model.habitopersonal.CategoriaDelHabito;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Las categorias de habito (dimensiones de Training) de la formacion de la persona (spec 038). SER las usa para no ofrecer ni rotular una
 * categoria de otra formacion: el adaptador delega en {@code habits.api.CategoriasDeLaFormacionFinder}, que es donde viven.
 */
public interface ConsultarCategoriasDeHabitoPort {

    /** Las ACTIVAS de la formacion de la persona, en el orden de Administracion; vacia si no pertenece a ninguna. */
    List<CategoriaDelHabito> deLaFormacionDe(UserId participanteId);
}
