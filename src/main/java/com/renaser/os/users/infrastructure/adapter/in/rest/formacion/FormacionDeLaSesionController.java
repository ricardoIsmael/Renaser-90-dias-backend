package com.renaser.os.users.infrastructure.adapter.in.rest.formacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.shared.web.security.SinInterruptor;
import com.renaser.os.users.application.ports.in.formacion.ConsultarFormacionDeLaSesionUseCase;
import com.renaser.os.users.application.ports.in.formacion.ElegirFormacionActivaUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La formación de la sesión y la elección de la activa del personal (spec 022, FR-012, FR-013). La activa la guarda el servidor: la app no manda la
 * formación en cada pedido, porque es una preferencia de uso y no un permiso.
 */
@RestController
@RequestMapping("/api/v1/me/formacion")
@SinInterruptor(motivo = "Es parte de la sesión: la app lo lee al entrar y al volver al frente; sin esto el personal no podría ni elegir en qué formación trabaja")
public class FormacionDeLaSesionController {

    private final ConsultarFormacionDeLaSesionUseCase consultar;
    private final ElegirFormacionActivaUseCase elegir;

    public FormacionDeLaSesionController(ConsultarFormacionDeLaSesionUseCase consultar, ElegirFormacionActivaUseCase elegir) {
        this.consultar = consultar;
        this.elegir = elegir;
    }

    @RequiresPermission(Permission.USE_APP)
    @GetMapping
    public FormacionDeLaSesionResponse miFormacion(@ActorAutenticado UserId actor) {
        return FormacionDeLaSesionResponse.from(consultar.consultar(actor));
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "elige entre SUS formaciones asignadas; el servicio responde 403 si no es personal o no la tiene")
    @PutMapping("/activa")
    public FormacionDeLaSesionResponse elegirActiva(@ActorAutenticado UserId actor, @RequestBody @Valid ElegirFormacionRequest request) {
        return FormacionDeLaSesionResponse.from(elegir.elegir(actor, Formacion.desde(request.codigo())));
    }

    public record ElegirFormacionRequest(@NotBlank String codigo) {
    }
}
