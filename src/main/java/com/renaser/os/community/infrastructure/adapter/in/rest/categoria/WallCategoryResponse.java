package com.renaser.os.community.infrastructure.adapter.in.rest.categoria;

import com.renaser.os.community.domain.model.categoria.CategoriaMuro;

/** GET /api/v1/wall/categories — lo unico que el movil necesita para pintar las pildoras
 * (wall-categories/schema.ts:82-90): sin `isActive`/`isSystem`, la app solo recibe las
 * activas. */
public record WallCategoryResponse(String key, String label, String emoji, int order) {

    public static WallCategoryResponse from(CategoriaMuro categoria) {
        return new WallCategoryResponse(categoria.clave(), categoria.etiqueta(), categoria.emoji(),
                categoria.orden());
    }
}
