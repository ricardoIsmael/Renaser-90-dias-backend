package com.renaser.os.habits.application.ports.in.habitoadmin;

import com.renaser.os.habits.domain.model.audiencia.CodigoDeRol;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/** Panel admin de catalogo (hueco #11, docs/PLAN_INTEGRACION_FRONTEND.md #11). Solo ADMIN/ALCHEMIST. */
public interface ConsultarCatalogoAdminUseCase {

    /** Catalogo SISTEMA completo, activos e inactivos — el panel decide como pintar cada estado. */
    List<HabitoDelCatalogo> listar(UserId actorId);

    /**
     * Spec 001: solo los que aplican a {@code rol} según su ÚLTIMA audiencia guardada (los "para todos" incluidos).
     * {@code rol} nulo = todo el catálogo.
     */
    List<HabitoDelCatalogo> listar(UserId actorId, CodigoDeRol rol);
}
