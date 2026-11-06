package com.renaser.os.community.infrastructure.adapter.in.rest.sobrenombre;

import com.renaser.os.community.application.ports.in.sobrenombre.AdministrarSobrenombresUseCase;
import com.renaser.os.community.application.ports.in.sobrenombre.AdministrarSobrenombresUseCase.PalabrasProhibidas;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spec 046 (D-331): Administración y Alquimista ({@code MODERATE_WALL}) ven, cambian y quitan sobrenombres, y editan las palabras
 * prohibidas. Cambiar o quitar pide un motivo (queda en la constancia; a la persona se le avisa sin él, D7).
 */
@RestController
@RequestMapping("/api/v1/admin")
@Funcionalidad("muro")
public class SobrenombresAdminController {

    private final AdministrarSobrenombresUseCase sobrenombres;

    public SobrenombresAdminController(AdministrarSobrenombresUseCase sobrenombres) {
        this.sobrenombres = sobrenombres;
    }

    @RequiresPermission(Permission.MODERATE_WALL)
    @GetMapping("/sobrenombres")
    public List<FilaResponse> listar(@ActorAutenticado UserId actor, @RequestParam(name = "q", required = false) String buscar) {
        return sobrenombres.listar(actor, buscar).stream()
                .map(f -> new FilaResponse(f.persona().toString(), f.sobrenombre(), f.nombreReal(), f.desde()))
                .toList();
    }

    @RequiresPermission(Permission.MODERATE_WALL)
    @GetMapping("/personas/{id}/sobrenombre")
    public SobrenombreDePersonaResponse de(@ActorAutenticado UserId actor, @PathVariable UUID id) {
        return new SobrenombreDePersonaResponse(sobrenombres.de(actor, UserId.of(id)));
    }

    @RequiresPermission(Permission.MODERATE_WALL)
    @PutMapping("/personas/{id}/sobrenombre")
    public SobrenombreDePersonaResponse cambiar(@ActorAutenticado UserId actor, @PathVariable UUID id,
                                               @Valid @RequestBody CambiarRequest request) {
        return new SobrenombreDePersonaResponse(sobrenombres.cambiar(actor, UserId.of(id), request.sobrenombre(), request.motivo()));
    }

    @RequiresPermission(Permission.MODERATE_WALL)
    @DeleteMapping("/personas/{id}/sobrenombre")
    public SobrenombreDePersonaResponse quitar(@ActorAutenticado UserId actor, @PathVariable UUID id, @RequestParam String motivo) {
        return new SobrenombreDePersonaResponse(sobrenombres.cambiar(actor, UserId.of(id), null, motivo));
    }

    @RequiresPermission(Permission.MODERATE_WALL)
    @GetMapping("/sobrenombres/palabras-prohibidas")
    public PalabrasResponse palabras(@ActorAutenticado UserId actor) {
        return PalabrasResponse.from(sobrenombres.palabras(actor));
    }

    @RequiresPermission(Permission.MODERATE_WALL)
    @PostMapping("/sobrenombres/palabras-prohibidas")
    public PalabrasResponse agregar(@ActorAutenticado UserId actor, @Valid @RequestBody PalabraRequest request) {
        return PalabrasResponse.from(sobrenombres.agregarPalabra(actor, request.palabra()));
    }

    @RequiresPermission(Permission.MODERATE_WALL)
    @DeleteMapping("/sobrenombres/palabras-prohibidas/{palabra}")
    public PalabrasResponse quitarPalabra(@ActorAutenticado UserId actor, @PathVariable String palabra) {
        return PalabrasResponse.from(sobrenombres.quitarPalabra(actor, palabra));
    }

    public record CambiarRequest(@NotBlank @Size(max = 40) String sobrenombre, @NotBlank @Size(min = 3, max = 200) String motivo) {
    }

    public record PalabraRequest(@NotBlank @Size(max = 40) String palabra) {
    }

    public record FilaResponse(String personaId, String sobrenombre, String nombreReal, Instant desde) {
    }

    public record SobrenombreDePersonaResponse(String sobrenombre) {
    }

    public record PalabrasResponse(int deLaListaBase, List<String> deAdministracion) {
        static PalabrasResponse from(PalabrasProhibidas p) {
            return new PalabrasResponse(p.deLaListaBase(), p.deAdministracion());
        }
    }
}
