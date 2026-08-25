package com.renaser.os.onboarding.infrastructure.adapter.in.rest.grabacionv90;

import com.renaser.os.onboarding.application.ports.in.grabacionv90.ListarGrabacionesV90UseCase;
import com.renaser.os.onboarding.application.ports.in.grabacionv90.RegistrarGrabacionV90UseCase;
import com.renaser.os.onboarding.application.ports.in.grabacionv90.RegistrarGrabacionV90UseCase.RegistrarGrabacionV90Command;
import com.renaser.os.onboarding.application.ports.in.grabacionv90.ValidarV90UseCase;
import com.renaser.os.onboarding.application.ports.in.grabacionv90.ValidarV90UseCase.ConsultarEstadoV90Query;
import com.renaser.os.onboarding.application.ports.in.grabacionv90.ValidarV90UseCase.SolicitarValidacionV90Command;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/onboarding/v90-recordings")
public class GrabacionV90Controller {

    private final RegistrarGrabacionV90UseCase registrarUseCase;
    private final ListarGrabacionesV90UseCase listarUseCase;
    private final ValidarV90UseCase validarUseCase;

    public GrabacionV90Controller(RegistrarGrabacionV90UseCase registrarUseCase,
                                   ListarGrabacionesV90UseCase listarUseCase, ValidarV90UseCase validarUseCase) {
        this.registrarUseCase = registrarUseCase;
        this.listarUseCase = listarUseCase;
        this.validarUseCase = validarUseCase;
    }

    @PostMapping
    public ResponseEntity<GrabacionV90Response> registrar(@RequestHeader("X-Actor-Id") String actorId,
                                                            @Valid @RequestBody RegistrarGrabacionV90Request request) {
        var comando = new RegistrarGrabacionV90Command(UserId.of(actorId), request.phase(), request.axis(),
                request.index(), request.questionKey(), request.mediaId(), request.durationSeconds(),
                request.transcript());
        var grabacion = registrarUseCase.registrar(comando);
        return ResponseEntity.status(HttpStatus.CREATED).body(GrabacionV90Response.from(grabacion));
    }

    @GetMapping
    public List<GrabacionV90Response> listar(@RequestHeader("X-Actor-Id") String actorId) {
        return listarUseCase.listar(UserId.of(actorId)).stream().map(GrabacionV90Response::from).toList();
    }

    /** 202 de inmediato: el trabajo real corre async (CLAUDE.MD §7). */
    @PostMapping("/{id}/validation")
    public ResponseEntity<ValidacionV90Response> solicitarValidacion(@RequestHeader("X-Actor-Id") String actorId,
                                                                       @PathVariable("id") Long id) {
        validarUseCase.solicitarValidacion(new SolicitarValidacionV90Command(UserId.of(actorId), id));
        return ResponseEntity.accepted().body(ValidacionV90Response.accepted());
    }

    /** GET de polling. */
    @GetMapping("/{id}/validation")
    public ValidacionV90Response consultarValidacion(@RequestHeader("X-Actor-Id") String actorId,
                                                       @PathVariable("id") Long id) {
        var estado = validarUseCase.consultarEstado(new ConsultarEstadoV90Query(UserId.of(actorId), id));
        return ValidacionV90Response.from(estado);
    }
}
