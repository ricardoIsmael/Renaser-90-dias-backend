package com.renaser.os.onboarding.infrastructure.adapter.in.rest.estado;

import com.renaser.os.onboarding.application.ports.in.estado.AceptarHitoOnboardingUseCase;
import com.renaser.os.onboarding.application.ports.in.estado.AceptarHitoOnboardingUseCase.AceptarHitoCommand;
import com.renaser.os.onboarding.application.ports.in.estado.AvanzarEstadoUseCase;
import com.renaser.os.onboarding.application.ports.in.estado.AvanzarEstadoUseCase.AvanzarEstadoCommand;
import com.renaser.os.onboarding.application.ports.in.estado.CompletarOnboardingUseCase;
import com.renaser.os.onboarding.application.ports.in.estado.CompletarOnboardingUseCase.CompletarOnboardingCommand;
import com.renaser.os.onboarding.application.ports.in.estado.ObtenerEstadoOnboardingUseCase;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
public class EstadoOnboardingController {

    private final ObtenerEstadoOnboardingUseCase obtenerUseCase;
    private final AvanzarEstadoUseCase avanzarUseCase;
    private final AceptarHitoOnboardingUseCase aceptarUseCase;
    private final CompletarOnboardingUseCase completarUseCase;

    public EstadoOnboardingController(ObtenerEstadoOnboardingUseCase obtenerUseCase,
                                       AvanzarEstadoUseCase avanzarUseCase,
                                       AceptarHitoOnboardingUseCase aceptarUseCase,
                                       CompletarOnboardingUseCase completarUseCase) {
        this.obtenerUseCase = obtenerUseCase;
        this.avanzarUseCase = avanzarUseCase;
        this.aceptarUseCase = aceptarUseCase;
        this.completarUseCase = completarUseCase;
    }

    @GetMapping("/state")
    public EstadoOnboardingResponse obtener(@RequestHeader("X-Actor-Id") String actorId) {
        return EstadoOnboardingResponse.from(obtenerUseCase.obtener(UserId.of(actorId)));
    }

    @PutMapping("/state")
    public EstadoOnboardingResponse avanzar(@RequestHeader("X-Actor-Id") String actorId,
                                             @Valid @RequestBody AvanzarEstadoRequest request) {
        var comando = new AvanzarEstadoCommand(UserId.of(actorId), request.flow(), request.section(),
                request.step(), request.flowProgress());
        return EstadoOnboardingResponse.from(avanzarUseCase.avanzar(comando));
    }

    @PostMapping("/milestones")
    public EstadoOnboardingResponse aceptar(@RequestHeader("X-Actor-Id") String actorId,
                                             @Valid @RequestBody AceptarHitoRequest request) {
        var comando = new AceptarHitoCommand(UserId.of(actorId), request.milestone());
        return EstadoOnboardingResponse.from(aceptarUseCase.aceptar(comando));
    }

    @PostMapping("/complete")
    public EstadoOnboardingResponse completar(@RequestHeader("X-Actor-Id") String actorId) {
        var comando = new CompletarOnboardingCommand(UserId.of(actorId));
        return EstadoOnboardingResponse.from(completarUseCase.completar(comando));
    }
}
