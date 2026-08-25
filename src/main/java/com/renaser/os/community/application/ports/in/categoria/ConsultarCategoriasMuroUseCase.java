package com.renaser.os.community.application.ports.in.categoria;

import com.renaser.os.community.domain.model.categoria.CategoriaMuro;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Set;

public interface ConsultarCategoriasMuroUseCase {

    /** Sin guarda de rol: catalogo publico para cualquiera con sesion
     * (wall-categories/service.ts:41-44). */
    List<CategoriaMuro> listarPublicas();

    List<CategoriaFilaAdmin> listarParaPanel(UserId actorId);

    Set<String> clavesExistentes();

    record CategoriaFilaAdmin(CategoriaMuro categoria, int cantidadPublicaciones) {
    }
}
