package com.renaser.os.community.infrastructure.adapter.in.rest.sobrenombre;

import com.renaser.os.community.application.ports.in.sobrenombre.MiSobrenombreUseCase;
import com.renaser.os.community.application.ports.in.sobrenombre.MiSobrenombreUseCase.MiSobrenombre;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Spec 046 (D-331): el sobrenombre de la propia persona en el Muro. {@code GET} lo lee (con desde cuándo puede cambiarlo y si dijo
 * «Ahora no» a la invitación del día 7); {@code PUT} lo elige o lo cambia; {@code DELETE} lo quita; {@code POST /descartar} es el
 * «Ahora no» (la invitación no vuelve, D2).
 */
@RestController
@RequestMapping("/api/v1/me/sobrenombre")
@Funcionalidad("muro")
public class MiSobrenombreController {

    private final MiSobrenombreUseCase sobrenombre;

    public MiSobrenombreController(MiSobrenombreUseCase sobrenombre) {
        this.sobrenombre = sobrenombre;
    }

    @RequiresPermission(Permission.USE_APP)
    @GetMapping
    public MiSobrenombreResponse leer(@ActorAutenticado UserId actor) {
        return MiSobrenombreResponse.from(sobrenombre.mio(actor));
    }

    @RequiresPermission(Permission.USE_APP)
    @PutMapping
    public MiSobrenombreResponse elegir(@ActorAutenticado UserId actor, @Valid @RequestBody ElegirRequest request) {
        return MiSobrenombreResponse.from(sobrenombre.elegir(actor, request.sobrenombre()));
    }

    @RequiresPermission(Permission.USE_APP)
    @DeleteMapping
    public MiSobrenombreResponse quitar(@ActorAutenticado UserId actor) {
        return MiSobrenombreResponse.from(sobrenombre.quitar(actor));
    }

    @RequiresPermission(Permission.USE_APP)
    @PostMapping("/descartar")
    public MiSobrenombreResponse descartar(@ActorAutenticado UserId actor) {
        return MiSobrenombreResponse.from(sobrenombre.descartarInvitacion(actor));
    }

    public record ElegirRequest(@NotBlank @Size(max = 40) String sobrenombre) {
    }

    public record MiSobrenombreResponse(String sobrenombre, Instant puedeCambiarDesde, boolean invitacionDescartada) {
        static MiSobrenombreResponse from(MiSobrenombre m) {
            return new MiSobrenombreResponse(m.sobrenombre(), m.puedeCambiarDesde(), m.invitacionDescartada());
        }
    }
}
