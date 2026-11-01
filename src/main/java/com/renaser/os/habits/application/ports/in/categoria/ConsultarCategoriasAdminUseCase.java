package com.renaser.os.habits.application.ports.in.categoria;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/** Panel de Administracion: las categorias de habito de una formacion, o de todas. Solo ADMIN/ALCHEMIST. */
public interface ConsultarCategoriasAdminUseCase {

    /** {@code formacion} nula = las de las tres, cada una con la suya (el filtro «Todas» solo lee). */
    List<CategoriaDelCatalogo> listar(UserId actorId, Formacion formacion);
}
