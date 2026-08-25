package com.renaser.os.rocks.infrastructure.adapter.in.rest.verdugo;

import com.renaser.os.rocks.application.ports.in.verdugo.ConsultarEventosVerdugoUseCase;
import com.renaser.os.rocks.application.ports.in.verdugo.RegistrarEventoVerdugoUseCase;
import com.renaser.os.rocks.application.ports.in.verdugo.RegistrarEventoVerdugoUseCase.RegistrarEventoVerdugoCommand;
import com.renaser.os.rocks.domain.model.verdugo.DestinoVerdugo;
import com.renaser.os.rocks.domain.model.verdugo.ResultadoVerdugo;
import com.renaser.os.shared.domain.UserId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Contrato preservado del repo viejo: `POST/GET /api/v1/enforcer-events` (ahora implementado por `rocks`). */
@RestController
@RequestMapping("/api/v1/enforcer-events")
public class VerdugoController {

    private final RegistrarEventoVerdugoUseCase registrarUseCase;
    private final ConsultarEventosVerdugoUseCase consultarUseCase;

    public VerdugoController(RegistrarEventoVerdugoUseCase registrarUseCase,
                              ConsultarEventosVerdugoUseCase consultarUseCase) {
        this.registrarUseCase = registrarUseCase;
        this.consultarUseCase = consultarUseCase;
    }

    @GetMapping
    public List<EventoVerdugoResponse> listar(@RequestHeader("X-Actor-Id") String actorId) {
        return consultarUseCase.misEventos(UserId.of(actorId)).stream().map(EventoVerdugoResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<EventoVerdugoResponse> registrar(@RequestHeader("X-Actor-Id") String actorId,
                                                             @Valid @RequestBody RegistrarEventoVerdugoRequest request) {
        var evento = registrarUseCase.registrar(new RegistrarEventoVerdugoCommand(UserId.of(actorId),
                DestinoVerdugo.valueOf(request.destinoTipo()), request.destinoId(), request.disparadoEn(),
                ResultadoVerdugo.valueOf(request.resultado())));
        return ResponseEntity.status(HttpStatus.CREATED).body(EventoVerdugoResponse.from(evento));
    }
}
