package com.renaser.os.users.infrastructure.adapter.in.rest.admin;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.users.application.ports.in.formacion.AsignarFormacionesDelPersonalUseCase;
import com.renaser.os.users.application.ports.in.formacion.CambiarDuracionDelProgramaUseCase;
import com.renaser.os.users.application.ports.in.formacion.CambiarDuracionDelProgramaUseCase.CambiarDuracionCommand;
import com.renaser.os.users.application.ports.in.formacion.AsignarFormacionesDelPersonalUseCase.AsignarFormacionesCommand;
import com.renaser.os.users.application.ports.in.formacion.ConfigurarFormacionUseCase;
import com.renaser.os.users.application.ports.in.formacion.ConsultarFormacionDeUnaPersonaUseCase;
import com.renaser.os.users.application.ports.in.formacion.ConfigurarFormacionUseCase.ConfigurarFormacionCommand;
import com.renaser.os.users.application.ports.in.formacion.TrasladarAprendizDeFormacionUseCase;
import com.renaser.os.users.application.ports.in.formacion.TrasladarAprendizDeFormacionUseCase.TrasladarCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lo que Administración y Alquimista cambian de las formaciones (spec 022): sus datos, las formaciones del personal y el traslado de un aprendiz.
 * El guard real es {@code RequireAdminGuard} en cada servicio (la matriz falla abierta para MENTOR, A-1). Las cifras de «Formaciones» las sirve
 * {@code leadership}, que junta varios módulos.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Funcionalidad("admin.personas")
public class FormacionesAdminController {

    private final ConfigurarFormacionUseCase configurar;
    private final AsignarFormacionesDelPersonalUseCase asignar;
    private final TrasladarAprendizDeFormacionUseCase trasladar;
    private final CambiarDuracionDelProgramaUseCase cambiarDuracion;
    private final ConsultarFormacionDeUnaPersonaUseCase consultarPersona;

    public FormacionesAdminController(ConfigurarFormacionUseCase configurar, AsignarFormacionesDelPersonalUseCase asignar,
                                      TrasladarAprendizDeFormacionUseCase trasladar, CambiarDuracionDelProgramaUseCase cambiarDuracion,
                                      ConsultarFormacionDeUnaPersonaUseCase consultarPersona) {
        this.configurar = configurar;
        this.asignar = asignar;
        this.trasladar = trasladar;
        this.cambiarDuracion = cambiarDuracion;
        this.consultarPersona = consultarPersona;
    }

    @RequiresPermission(value = Permission.MANAGE_TRAINEES, scope = "el guard real es RequireAdminGuard: ADMIN/ALCHEMIST activos")
    @GetMapping("/users/{id}/formaciones")
    public FormacionDeUnaPersonaResponse deUnaPersona(@PathVariable UUID id, @ActorAutenticado UserId actor) {
        return FormacionDeUnaPersonaResponse.from(consultarPersona.consultar(actor, UserId.of(id)));
    }

    @RequiresPermission(value = Permission.MANAGE_ROLES, scope = "el guard real es RequireAdminGuard: ADMIN/ALCHEMIST activos")
    @PutMapping("/formaciones/{codigo}")
    public FormacionResponse configurar(@PathVariable String codigo, @ActorAutenticado UserId actor,
                                        @RequestBody @Valid ConfigurarRequest request) {
        return FormacionResponse.from(configurar.configurar(new ConfigurarFormacionCommand(actor, Formacion.desde(codigo),
                request.nombre(), request.descripcion(), request.activa(), request.duracionesDias(), request.version())));
    }

    @RequiresPermission(value = Permission.MANAGE_ROLES, scope = "el guard real es RequireAdminGuard: ADMIN/ALCHEMIST activos")
    @PutMapping("/users/{id}/formaciones")
    public ResponseEntity<Void> asignarAlPersonal(@PathVariable UUID id, @ActorAutenticado UserId actor,
                                                  @RequestBody @Valid AsignarRequest request) {
        asignar.asignar(new AsignarFormacionesCommand(actor, UserId.of(id),
                request.formaciones().stream().map(Formacion::desde).collect(Collectors.toSet()),
                request.activa() == null || request.activa().isBlank() ? null : Formacion.desde(request.activa())));
        return ResponseEntity.noContent().build();
    }

    @RequiresPermission(value = Permission.MANAGE_TRAINEES, scope = "el guard real es RequireAdminGuard: ADMIN/ALCHEMIST activos")
    @PostMapping("/trainees/{id}/formacion")
    public ResponseEntity<Void> trasladar(@PathVariable UUID id, @ActorAutenticado UserId actor,
                                          @RequestBody @Valid TrasladarRequest request) {
        trasladar.trasladar(new TrasladarCommand(actor, UserId.of(id), Formacion.desde(request.formacion()), request.duracionDias(),
                request.darPorTerminada(), request.motivo()));
        return ResponseEntity.noContent().build();
    }

    /** Spec 025 (025-2 A): cambia la duración del programa de quien ya empezó, con motivo. Conserva su día. */
    @RequiresPermission(value = Permission.MANAGE_TRAINEES, scope = "el guard real es RequireAdminGuard: ADMIN/ALCHEMIST activos")
    @PostMapping("/trainees/{id}/duracion")
    public ResponseEntity<Void> cambiarLaDuracion(@PathVariable UUID id, @ActorAutenticado UserId actor,
                                                  @RequestBody @Valid CambiarDuracionRequest request) {
        cambiarDuracion.cambiar(new CambiarDuracionCommand(actor, UserId.of(id), request.duracionDias(), request.motivo()));
        return ResponseEntity.noContent().build();
    }

    public record ConfigurarRequest(@NotBlank String nombre, String descripcion, boolean activa,
                                    @NotEmpty List<Integer> duracionesDias, int version) {
    }

    public record AsignarRequest(@NotEmpty List<String> formaciones, String activa) {
    }

    public record CambiarDuracionRequest(@NotNull Integer duracionDias, @NotBlank String motivo) {
    }

    public record TrasladarRequest(@NotBlank String formacion, @NotNull Integer duracionDias, boolean darPorTerminada, String motivo) {
    }
}
