package com.renaser.os.community.infrastructure.adapter.in.rest.categoria;

import com.renaser.os.community.application.ports.in.categoria.ConsultarCategoriasMuroUseCase.CategoriaFilaAdmin;

/** Fila del panel. `postCount` decide si el boton de borrar se ofrece o se cambia por
 * "retirar" (wall-categories/schema.ts:92-98). */
public record WallCategoryAdminResponse(String key, String label, String emoji, int order, boolean isActive,
                                         boolean isSystem, int postCount) {

    public static WallCategoryAdminResponse from(CategoriaFilaAdmin fila) {
        return new WallCategoryAdminResponse(fila.categoria().clave(), fila.categoria().etiqueta(),
                fila.categoria().emoji(), fila.categoria().orden(), fila.categoria().activa(),
                fila.categoria().esSistema(), fila.cantidadPublicaciones());
    }
}
