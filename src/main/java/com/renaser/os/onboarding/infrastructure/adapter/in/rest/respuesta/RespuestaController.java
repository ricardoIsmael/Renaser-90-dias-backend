package com.renaser.os.onboarding.infrastructure.adapter.in.rest.respuesta;

import com.renaser.os.onboarding.application.ports.in.respuesta.GuardarRespuestaUseCase;
import com.renaser.os.onboarding.application.ports.in.respuesta.GuardarRespuestaUseCase.GuardarRespuestaCommand;
import com.renaser.os.onboarding.application.ports.in.respuesta.ObtenerRespuestasUseCase;
import com.renaser.os.onboarding.application.ports.in.respuesta.ObtenerRespuestasUseCase.ObtenerRespuestasQuery;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
public class RespuestaController {

    private final GuardarRespuestaUseCase guardarRespuestaUseCase;
    private final ObtenerRespuestasUseCase obtenerRespuestasUseCase;

    public RespuestaController(GuardarRespuestaUseCase guardarRespuestaUseCase,
                                ObtenerRespuestasUseCase obtenerRespuestasUseCase) {
        this.guardarRespuestaUseCase = guardarRespuestaUseCase;
        this.obtenerRespuestasUseCase = obtenerRespuestasUseCase;
    }

    @RequiresPermission(Permission.USE_APP)
    @PostMapping("/answers")
    public ResponseEntity<RespuestaResponse> guardar(@ActorAutenticado UserId actor,
                                                       @Valid @RequestBody GuardarRespuestaRequest request) {
        var comando = new GuardarRespuestaCommand(actor, request.questionId(), request.textValue(),
                request.numberValue(), request.booleanValue(), request.scaleValue(), request.jsonValue(),
                request.mediaId());
        var respuesta = guardarRespuestaUseCase.guardar(comando);
        return ResponseEntity.status(HttpStatus.OK).body(RespuestaResponse.from(respuesta));
    }

    /** Respuestas ya guardadas del actor para el flujo pedido, agrupadas por seccion (hidratar onboarding a medio terminar). */
    @RequiresPermission(Permission.USE_APP)
    @GetMapping("/answers")
    public RespuestasAgrupadasResponse obtener(@ActorAutenticado UserId actor,
                                                 @RequestParam("flow") String flow) {
        var query = new ObtenerRespuestasQuery(actor, flow);
        return RespuestasAgrupadasResponse.from(flow, obtenerRespuestasUseCase.obtener(query));
    }
}
