package com.renaser.os.habits.application.ports.in.categoria;

import com.renaser.os.habits.domain.model.categoria.CategoriaHabito;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Las categorias activas (las dimensiones de Training) de la formacion de quien pregunta (spec 023, historia 4). El servidor decide la
 * formacion: la de su programa o, si no tiene, la que tiene activa; nadie pide la de otra. Administracion/Alquimista/Soporte TI, que ven todas,
 * pueden elegirla con {@code formacionPedida}.
 */
public interface ConsultarCategoriasDeMiFormacionUseCase {

    CategoriasDeLaFormacion consultar(UserId actorId, Formacion formacionPedida);

    /** {@code formacion} vacia = la persona no pertenece a ninguna (personal sin asignar): no hay categorias, no un error. */
    record CategoriasDeLaFormacion(Optional<Formacion> formacion, List<CategoriaHabito> categorias) {
    }
}
