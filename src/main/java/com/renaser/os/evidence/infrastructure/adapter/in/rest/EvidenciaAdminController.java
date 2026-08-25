package com.renaser.os.evidence.infrastructure.adapter.in.rest;

import com.renaser.os.evidence.application.ports.in.evidencia.AnularVeredictoUseCase;
import com.renaser.os.evidence.application.ports.in.evidencia.AnularVeredictoUseCase.AnularVeredictoCommand;
import com.renaser.os.evidence.application.ports.in.evidencia.RevisarManualmenteUseCase;
import com.renaser.os.evidence.application.ports.in.evidencia.RevisarManualmenteUseCase.RevisarManualmenteCommand;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Solo ADMIN/ALCHEMIST — gateado dentro de {@code EvidenciaService}, no en el controller (CLAUDE.MD §5.4.6). */
@RestController
@RequestMapping("/api/v1/admin/evidence")
public class EvidenciaAdminController {

    private final RevisarManualmenteUseCase revisarUseCase;
    private final AnularVeredictoUseCase anularUseCase;

    public EvidenciaAdminController(RevisarManualmenteUseCase revisarUseCase, AnularVeredictoUseCase anularUseCase) {
        this.revisarUseCase = revisarUseCase;
        this.anularUseCase = anularUseCase;
    }

    @PostMapping("/{id}/review")
    public EvidenciaResponse revisar(@RequestHeader("X-Actor-Id") String actorId, @PathVariable UUID id,
                                      @Valid @RequestBody RevisarManualmenteRequest request) {
        var evidencia = revisarUseCase.revisar(new RevisarManualmenteCommand(UserId.of(actorId), EvidenciaId.of(id),
                request.aprobar(), request.notas()));
        return EvidenciaResponse.from(evidencia);
    }

    @PostMapping("/{id}/void")
    public EvidenciaResponse anular(@RequestHeader("X-Actor-Id") String actorId, @PathVariable UUID id,
                                     @Valid @RequestBody AnularVeredictoRequest request) {
        var evidencia = anularUseCase.anular(new AnularVeredictoCommand(UserId.of(actorId), EvidenciaId.of(id),
                request.notas()));
        return EvidenciaResponse.from(evidencia);
    }
}
