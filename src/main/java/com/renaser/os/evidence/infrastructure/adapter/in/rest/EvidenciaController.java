package com.renaser.os.evidence.infrastructure.adapter.in.rest;

import com.renaser.os.evidence.application.ports.in.evidencia.ConsultarEvidenciaUseCase;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Actor resuelto por header {@code X-Actor-Id} (temporal, D-29 de {@code users}, mismo
 * patrón que {@code points}/{@code phasecontracts}/{@code rocks}/{@code habits}).
 * Autoservicio con excepción admin: el dueño ve su propia evidencia; cualquier otro
 * actor necesita ser ADMIN/ALCHEMIST (aplicado dentro de {@code EvidenciaService}).
 */
@RestController
@RequestMapping("/api/v1/evidence")
public class EvidenciaController {

    private final ConsultarEvidenciaUseCase consultarUseCase;

    public EvidenciaController(ConsultarEvidenciaUseCase consultarUseCase) {
        this.consultarUseCase = consultarUseCase;
    }

    @GetMapping("/{id}")
    public EvidenciaResponse porId(@RequestHeader("X-Actor-Id") String actorId, @PathVariable UUID id) {
        var evidencia = consultarUseCase.porId(UserId.of(actorId), EvidenciaId.of(id));
        return EvidenciaResponse.from(evidencia);
    }
}
