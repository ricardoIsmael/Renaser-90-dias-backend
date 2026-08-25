package com.renaser.os.points.infrastructure.adapter.in.rest.puntaje;

import com.renaser.os.points.application.ports.in.puntaje.AjustarPuntosManualmenteUseCase;
import com.renaser.os.points.application.ports.in.puntaje.AjustarPuntosManualmenteUseCase.AjustarPuntosManualmenteCommand;
import com.renaser.os.points.application.ports.in.puntaje.ConsultarPuntajeUseCase;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/points")
public class PuntajeController {

    private final ConsultarPuntajeUseCase consultarPuntajeUseCase;
    private final AjustarPuntosManualmenteUseCase ajustarPuntosManualmenteUseCase;

    public PuntajeController(ConsultarPuntajeUseCase consultarPuntajeUseCase,
                              AjustarPuntosManualmenteUseCase ajustarPuntosManualmenteUseCase) {
        this.consultarPuntajeUseCase = consultarPuntajeUseCase;
        this.ajustarPuntosManualmenteUseCase = ajustarPuntosManualmenteUseCase;
    }

    @GetMapping("/{participanteId}")
    public PuntajeResponse consultar(@RequestHeader("X-Actor-Id") String actorId,
                                     @PathVariable String participanteId) {
        return PuntajeResponse.from(
                consultarPuntajeUseCase.consultar(UserId.of(actorId), UserId.of(participanteId)));
    }

    @PostMapping("/adjustments")
    public ResponseEntity<AjustePuntosResponse> ajustarManualmente(@RequestHeader("X-Actor-Id") String actorId,
                                                                     @RequestBody @Valid AjustarPuntosManualRequest request) {
        var ajuste = ajustarPuntosManualmenteUseCase.ajustarManualmente(new AjustarPuntosManualmenteCommand(
                UserId.of(request.participanteId()), request.delta(), request.nota(), UserId.of(actorId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(AjustePuntosResponse.from(ajuste));
    }
}
