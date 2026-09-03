package com.renaser.os.rocks.infrastructure.adapter.in.rest.verdugo;

import com.renaser.os.rocks.application.ports.in.verdugo.ConsultarEventosVerdugoUseCase;
import com.renaser.os.rocks.application.ports.in.verdugo.RegistrarEventoVerdugoUseCase;
import com.renaser.os.rocks.application.ports.in.verdugo.RegistrarEventoVerdugoUseCase.RegistrarEventoVerdugoCommand;
import com.renaser.os.rocks.domain.model.verdugo.DestinoVerdugo;
import com.renaser.os.rocks.domain.model.verdugo.ResultadoVerdugo;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
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

    @RequiresPermission(Permission.FOLLOW_OWN_PROGRAM)
    @GetMapping
    public List<EventoVerdugoResponse> listar(@ActorAutenticado UserId actor) {
        return consultarUseCase.misEventos(actor).stream().map(EventoVerdugoResponse::from).toList();
    }

    @RequiresPermission(value = Permission.FOLLOW_OWN_PROGRAM, scope = "el destino registrado tiene que ser del propio actor")
    @PostMapping
    public ResponseEntity<EventoVerdugoResponse> registrar(@ActorAutenticado UserId actor,
                                                             @Valid @RequestBody RegistrarEventoVerdugoRequest request) {
        var evento = registrarUseCase.registrar(new RegistrarEventoVerdugoCommand(actor,
                DestinoVerdugo.valueOf(request.destinoTipo()), request.destinoId(), request.disparadoEn(),
                ResultadoVerdugo.valueOf(request.resultado())));
        return ResponseEntity.status(HttpStatus.CREATED).body(EventoVerdugoResponse.from(evento));
    }
}
