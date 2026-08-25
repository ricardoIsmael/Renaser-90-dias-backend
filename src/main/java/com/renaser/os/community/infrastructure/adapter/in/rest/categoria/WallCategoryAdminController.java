package com.renaser.os.community.infrastructure.adapter.in.rest.categoria;

import com.renaser.os.community.application.ports.in.categoria.ActualizarCategoriaMuroUseCase;
import com.renaser.os.community.application.ports.in.categoria.ActualizarCategoriaMuroUseCase.ActualizarCategoriaMuroCommand;
import com.renaser.os.community.application.ports.in.categoria.ConsultarCategoriasMuroUseCase;
import com.renaser.os.community.application.ports.in.categoria.CrearCategoriaMuroUseCase;
import com.renaser.os.community.application.ports.in.categoria.CrearCategoriaMuroUseCase.CrearCategoriaMuroCommand;
import com.renaser.os.community.application.ports.in.categoria.EliminarCategoriaMuroUseCase;
import com.renaser.os.community.application.ports.in.categoria.EliminarCategoriaMuroUseCase.EliminarCategoriaMuroCommand;
import com.renaser.os.community.application.ports.in.categoria.ReordenarCategoriasMuroUseCase;
import com.renaser.os.community.application.ports.in.categoria.ReordenarCategoriasMuroUseCase.ReordenarCategoriasMuroCommand;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Administracion del catalogo de categorias del Muro — solo ADMIN/ALCHEMIST
 * (wall-categories/service.ts:28-30). El codigo viejo solo tenia server actions para el
 * panel Next.js (sin ruta REST propia); esta es la ruta nueva para el panel que consuma
 * esta API (CM-13, docs/MODULO_COMMUNITY.md sec. 5). */
@RestController
@RequestMapping("/api/v1/admin/wall-categories")
public class WallCategoryAdminController {

    private final CrearCategoriaMuroUseCase crearUseCase;
    private final ActualizarCategoriaMuroUseCase actualizarUseCase;
    private final EliminarCategoriaMuroUseCase eliminarUseCase;
    private final ReordenarCategoriasMuroUseCase reordenarUseCase;
    private final ConsultarCategoriasMuroUseCase consultarUseCase;

    public WallCategoryAdminController(CrearCategoriaMuroUseCase crearUseCase,
                                        ActualizarCategoriaMuroUseCase actualizarUseCase,
                                        EliminarCategoriaMuroUseCase eliminarUseCase,
                                        ReordenarCategoriasMuroUseCase reordenarUseCase,
                                        ConsultarCategoriasMuroUseCase consultarUseCase) {
        this.crearUseCase = crearUseCase;
        this.actualizarUseCase = actualizarUseCase;
        this.eliminarUseCase = eliminarUseCase;
        this.reordenarUseCase = reordenarUseCase;
        this.consultarUseCase = consultarUseCase;
    }

    @GetMapping
    public List<WallCategoryAdminResponse> listar(@RequestHeader("X-Actor-Id") String actorId) {
        return consultarUseCase.listarParaPanel(UserId.of(actorId)).stream().map(WallCategoryAdminResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<WallCategoryAdminResponse> crear(@RequestHeader("X-Actor-Id") String actorId,
                                                             @RequestBody @Valid CrearWallCategoryRequest request) {
        var fila = crearUseCase.crear(new CrearCategoriaMuroCommand(UserId.of(actorId), request.key(),
                request.label(), request.emoji()));
        return ResponseEntity.status(HttpStatus.CREATED).body(WallCategoryAdminResponse.from(fila));
    }

    @PatchMapping("/{key}")
    public WallCategoryAdminResponse actualizar(@RequestHeader("X-Actor-Id") String actorId,
                                                 @PathVariable String key,
                                                 @RequestBody ActualizarWallCategoryRequest request) {
        var fila = actualizarUseCase.actualizar(new ActualizarCategoriaMuroCommand(UserId.of(actorId), key,
                request.label(), request.emoji(), request.isActive()));
        return WallCategoryAdminResponse.from(fila);
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> eliminar(@RequestHeader("X-Actor-Id") String actorId, @PathVariable String key) {
        eliminarUseCase.eliminar(new EliminarCategoriaMuroCommand(UserId.of(actorId), key));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reorder")
    public ResponseEntity<Void> reordenar(@RequestHeader("X-Actor-Id") String actorId,
                                           @RequestBody @Valid ReordenarWallCategoriesRequest request) {
        reordenarUseCase.reordenar(new ReordenarCategoriasMuroCommand(UserId.of(actorId), request.keys()));
        return ResponseEntity.noContent().build();
    }
}
