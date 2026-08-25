package com.renaser.os.onboarding.infrastructure.adapter.in.rest.cuestionario;

import com.renaser.os.onboarding.application.ports.in.cuestionario.ObtenerCuestionarioUseCase;
import com.renaser.os.onboarding.application.ports.in.cuestionario.ObtenerCuestionarioUseCase.ObtenerCuestionarioQuery;
import com.renaser.os.shared.domain.UserId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
public class CuestionarioController {

    private final ObtenerCuestionarioUseCase obtenerCuestionarioUseCase;

    public CuestionarioController(ObtenerCuestionarioUseCase obtenerCuestionarioUseCase) {
        this.obtenerCuestionarioUseCase = obtenerCuestionarioUseCase;
    }

    @GetMapping("/questionnaire")
    public CuestionarioResponse obtener(@RequestHeader("X-Actor-Id") String actorId,
                                         @RequestParam("flow") String flow) {
        var query = new ObtenerCuestionarioQuery(UserId.of(actorId), flow);
        return CuestionarioResponse.from(obtenerCuestionarioUseCase.obtener(query));
    }
}
