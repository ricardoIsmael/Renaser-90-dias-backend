package com.renaser.os.onboarding.infrastructure.adapter.in.rest.estado;

import com.renaser.os.onboarding.application.ports.in.estado.AceptarHitoOnboardingUseCase;
import com.renaser.os.onboarding.application.ports.in.estado.AceptarHitoOnboardingUseCase.AceptarHitoCommand;
import com.renaser.os.onboarding.application.ports.in.estado.AvanzarEstadoUseCase;
import com.renaser.os.onboarding.application.ports.in.estado.AvanzarEstadoUseCase.AvanzarEstadoCommand;
import com.renaser.os.onboarding.application.ports.in.estado.CompletarOnboardingUseCase;
import com.renaser.os.onboarding.application.ports.in.estado.CompletarOnboardingUseCase.CompletarOnboardingCommand;
import com.renaser.os.onboarding.application.ports.in.estado.ObtenerEstadoOnboardingUseCase;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @RequiresPermission(Permission.USE_APP)
    @GetMapping("/state")
    public EstadoOnboardingResponse obtener(@ActorAutenticado UserId actor) {
        return EstadoOnboardingResponse.from(obtenerUseCase.obtener(actor));
    }

    @RequiresPermission(Permission.USE_APP)
    @PutMapping("/state")
    public EstadoOnboardingResponse avanzar(@ActorAutenticado UserId actor,
                                             @Valid @RequestBody AvanzarEstadoRequest request) {
        var comando = new AvanzarEstadoCommand(actor, request.flow(), request.section(),
                request.step(), request.flowProgress());
        return EstadoOnboardingResponse.from(avanzarUseCase.avanzar(comando));
    }

    @RequiresPermission(Permission.USE_APP)
    @PostMapping("/milestones")
    public EstadoOnboardingResponse aceptar(@ActorAutenticado UserId actor,
                                             @Valid @RequestBody AceptarHitoRequest request) {
        var comando = new AceptarHitoCommand(actor, request.milestone());
        return EstadoOnboardingResponse.from(aceptarUseCase.aceptar(comando));
    }

    @RequiresPermission(Permission.USE_APP)
    @PostMapping("/complete")
    public EstadoOnboardingResponse completar(@ActorAutenticado UserId actor) {
        var comando = new CompletarOnboardingCommand(actor);
        return EstadoOnboardingResponse.from(completarUseCase.completar(comando));
    }
}
