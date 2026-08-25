package com.renaser.os.onboarding.infrastructure.adapter.in.rest.respuesta;

import com.renaser.os.onboarding.application.ports.in.respuesta.GuardarRespuestaUseCase;
import com.renaser.os.onboarding.application.ports.in.respuesta.GuardarRespuestaUseCase.GuardarRespuestaCommand;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
public class RespuestaController {

    private final GuardarRespuestaUseCase guardarRespuestaUseCase;

    public RespuestaController(GuardarRespuestaUseCase guardarRespuestaUseCase) {
        this.guardarRespuestaUseCase = guardarRespuestaUseCase;
    }

    @PostMapping("/answers")
    public ResponseEntity<RespuestaResponse> guardar(@RequestHeader("X-Actor-Id") String actorId,
                                                       @Valid @RequestBody GuardarRespuestaRequest request) {
        var comando = new GuardarRespuestaCommand(UserId.of(actorId), request.questionId(), request.textValue(),
                request.numberValue(), request.booleanValue(), request.scaleValue(), request.jsonValue(),
                request.mediaId());
        var respuesta = guardarRespuestaUseCase.guardar(comando);
        return ResponseEntity.status(HttpStatus.OK).body(RespuestaResponse.from(respuesta));
    }
}
