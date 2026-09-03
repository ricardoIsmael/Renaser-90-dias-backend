package com.renaser.os.academy.infrastructure.adapter.in.rest.recomendacion;

import com.renaser.os.academy.application.ports.in.recomendacion.ConsultarRecomendacionDiariaUseCase;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AA-01: espejo de `GET /api/v1/academia/recomendacion` (RenaserBack). Solo TRAINEE. */
@RestController
@RequestMapping("/api/v1/academia/recomendacion")
public class RecomendacionController {

    private final ConsultarRecomendacionDiariaUseCase recomendacionUseCase;

    public RecomendacionController(ConsultarRecomendacionDiariaUseCase recomendacionUseCase) {
        this.recomendacionUseCase = recomendacionUseCase;
    }

    @RequiresPermission(Permission.FOLLOW_OWN_PROGRAM)
    @GetMapping
    public RecomendacionResponse recomendacion(@ActorAutenticado UserId actorId) {
        return RecomendacionResponse.from(recomendacionUseCase.recomendacion(actorId));
    }
}
