package com.renaser.os.community.infrastructure.adapter.in.rest.categoria;

import com.renaser.os.community.application.ports.in.categoria.ConsultarCategoriasMuroUseCase;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** GET /api/v1/wall/categories — catalogo publico, cualquiera con sesion
 * (wall-categories/service.ts:41-44). El CRUD de administracion vive en
 * {@link WallCategoryAdminController}. */
@RestController
@RequestMapping("/api/v1/wall/categories")
public class WallCategoryController {

    private final ConsultarCategoriasMuroUseCase consultarUseCase;

    public WallCategoryController(ConsultarCategoriasMuroUseCase consultarUseCase) {
        this.consultarUseCase = consultarUseCase;
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "declarado segun el javadoc de la clase (cualquiera con sesion); hoy el handler no recibe actor y no ejecuta ningun guard")
    @GetMapping
    public Map<String, List<WallCategoryResponse>> listar() {
        return Map.of("categories", consultarUseCase.listarPublicas().stream().map(WallCategoryResponse::from).toList());
    }
}
