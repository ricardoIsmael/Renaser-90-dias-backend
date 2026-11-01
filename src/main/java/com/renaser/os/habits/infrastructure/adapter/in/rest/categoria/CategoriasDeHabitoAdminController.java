package com.renaser.os.habits.infrastructure.adapter.in.rest.categoria;

import com.renaser.os.habits.application.ports.in.categoria.ConsultarCategoriasAdminUseCase;
import com.renaser.os.habits.application.ports.in.categoria.GestionarCategoriasUseCase;
import com.renaser.os.habits.application.ports.in.categoria.GestionarCategoriasUseCase.ActualizarCategoriaCommand;
import com.renaser.os.habits.application.ports.in.categoria.GestionarCategoriasUseCase.CambiarActivaCommand;
import com.renaser.os.habits.application.ports.in.categoria.GestionarCategoriasUseCase.CrearCategoriaCommand;
import com.renaser.os.habits.application.ports.in.categoria.GestionarCategoriasUseCase.EliminarCategoriaCommand;
import com.renaser.os.habits.application.ports.in.categoria.GestionarCategoriasUseCase.OrdenarCategoriasCommand;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Administracion de las categorias de habito de cada formacion (spec 023, historia 2). Solo ADMIN/ALCHEMIST: el guard real vive en el
 * servicio ({@code HabitoAdminGuard}) y responde 403 a cualquier otro rol o a una cuenta suspendida. Las claves de las categorias
 * (CUERPO, DIPLOMADO_ESTUDIO…) son texto sin barras, asi que van en la ruta.
 */
@RestController
@RequestMapping("/api/v1/admin/habit-categories")
@Funcionalidad("admin.habitos")
public class CategoriasDeHabitoAdminController {

    private final ConsultarCategoriasAdminUseCase consultarUseCase;
    private final GestionarCategoriasUseCase gestionarUseCase;

    public CategoriasDeHabitoAdminController(ConsultarCategoriasAdminUseCase consultarUseCase,
                                             GestionarCategoriasUseCase gestionarUseCase) {
        this.consultarUseCase = consultarUseCase;
        this.gestionarUseCase = gestionarUseCase;
    }

    /** {@code formation} ausente = las de las tres formaciones (el filtro «Todas», solo lectura). */
    @RequiresPermission(Permission.MANAGE_HABIT_CATALOG)
    @GetMapping
    public List<CategoriaAdminResponse> listar(@ActorAutenticado UserId actor,
                                               @RequestParam(name = "formation", required = false) String formation) {
        Formacion formacion = formation == null || formation.isBlank() ? null : Formacion.desde(formation);
        return consultarUseCase.listar(actor, formacion).stream().map(CategoriaAdminResponse::from).toList();
    }

    @RequiresPermission(Permission.MANAGE_HABIT_CATALOG)
    @PostMapping
    public ResponseEntity<CategoriaAdminResponse> crear(@ActorAutenticado UserId actor,
                                                         @RequestBody @Valid CrearCategoriaRequest request) {
        var creada = gestionarUseCase.crear(new CrearCategoriaCommand(actor, Formacion.desde(request.formation()),
                request.name(), request.icon()));
        return ResponseEntity.status(HttpStatus.CREATED).body(CategoriaAdminResponse.from(creada));
    }

    /** Los nombres de campo ausentes no se tocan; {@code icon} vacio quita el icono. */
    @RequiresPermission(Permission.MANAGE_HABIT_CATALOG)
    @PutMapping("/{key}")
    public CategoriaAdminResponse actualizar(@ActorAutenticado UserId actor, @PathVariable String key,
                                              @RequestBody @Valid ActualizarCategoriaRequest request) {
        return CategoriaAdminResponse.from(gestionarUseCase.actualizar(new ActualizarCategoriaCommand(actor, key,
                request.name(), request.icon(), request.changesIcon())));
    }

    @RequiresPermission(Permission.MANAGE_HABIT_CATALOG)
    @PutMapping("/{key}/active")
    public CategoriaAdminResponse cambiarActiva(@ActorAutenticado UserId actor, @PathVariable String key,
                                                 @RequestBody @Valid CambiarActivaRequest request) {
        return CategoriaAdminResponse.from(gestionarUseCase.cambiarActiva(new CambiarActivaCommand(actor, key, request.isActive())));
    }

    @RequiresPermission(Permission.MANAGE_HABIT_CATALOG)
    @PutMapping("/order")
    public List<CategoriaAdminResponse> ordenar(@ActorAutenticado UserId actor, @RequestBody @Valid OrdenarCategoriasRequest request) {
        return gestionarUseCase.ordenar(new OrdenarCategoriasCommand(actor, Formacion.desde(request.formation()), request.keys()))
                .stream().map(CategoriaAdminResponse::from).toList();
    }

    @RequiresPermission(Permission.MANAGE_HABIT_CATALOG)
    @DeleteMapping("/{key}")
    public ResponseEntity<Void> eliminar(@ActorAutenticado UserId actor, @PathVariable String key) {
        gestionarUseCase.eliminar(new EliminarCategoriaCommand(actor, key));
        return ResponseEntity.noContent().build();
    }

    public record CrearCategoriaRequest(@NotBlank String formation, @NotBlank @Size(max = 80) String name, String icon) {
    }

    /** {@code changesIcon} distingue «no toques el icono» de «quitale el icono» (icon vacio). */
    public record ActualizarCategoriaRequest(@Size(max = 80) String name, String icon, boolean changesIcon) {
    }

    public record CambiarActivaRequest(@NotNull Boolean isActive) {
    }

    public record OrdenarCategoriasRequest(@NotBlank String formation, @NotNull List<String> keys) {
    }
}
