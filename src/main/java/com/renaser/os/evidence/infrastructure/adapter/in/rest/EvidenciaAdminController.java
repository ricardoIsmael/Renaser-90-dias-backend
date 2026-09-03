package com.renaser.os.evidence.infrastructure.adapter.in.rest;

import com.renaser.os.evidence.api.EstadoValidacion;
import com.renaser.os.evidence.application.ports.in.evidencia.AnularVeredictoUseCase;
import com.renaser.os.evidence.application.ports.in.evidencia.AnularVeredictoUseCase.AnularVeredictoCommand;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaAdminUseCase;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaAdminUseCase.ListarEvidenciaAdminComando;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase.TipoDestino;
import com.renaser.os.evidence.application.ports.in.evidencia.RevisarManualmenteUseCase;
import com.renaser.os.evidence.application.ports.in.evidencia.RevisarManualmenteUseCase.RevisarManualmenteCommand;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/** Solo ADMIN/ALCHEMIST — gateado dentro de {@code EvidenciaService}, no en el controller (CLAUDE.MD §5.4.6). */
@RestController
@RequestMapping("/api/v1/admin/evidence")
public class EvidenciaAdminController {

    private final RevisarManualmenteUseCase revisarUseCase;
    private final AnularVeredictoUseCase anularUseCase;
    private final ListarEvidenciaAdminUseCase listarUseCase;

    public EvidenciaAdminController(RevisarManualmenteUseCase revisarUseCase, AnularVeredictoUseCase anularUseCase,
                                     ListarEvidenciaAdminUseCase listarUseCase) {
        this.revisarUseCase = revisarUseCase;
        this.anularUseCase = anularUseCase;
        this.listarUseCase = listarUseCase;
    }

    /**
     * Listado del panel admin (hueco #20) — p. ej. {@code ?estado=REVISION_MANUAL} para
     * la cola de revisión humana. No hay default de {@code estado} en el servidor: el
     * panel lo pide explícito, mismo criterio "controller/servicio no adivina intención
     * de negocio" que el resto del módulo. Mismos filtros y mismo cursor que
     * {@code GET /api/v1/evidence}, pero sin el scoping de dueño/mentor: acá el único
     * gate es el rol (ver javadoc de {@link ListarEvidenciaAdminUseCase}).
     */
    @RequiresPermission(Permission.MANAGE_EVIDENCE)
    @GetMapping
    public EvidenciaPageResponse listar(@ActorAutenticado UserId actor,
                                         @RequestParam(required = false) UUID participanteId,
                                         @RequestParam(required = false) String estado,
                                         @RequestParam(required = false) String tipoDestino,
                                         @RequestParam(required = false) String desde,
                                         @RequestParam(required = false) String hasta,
                                         @RequestParam(required = false) String cursor) {
        var comando = new ListarEvidenciaAdminComando(actor,
                participanteId != null ? UserId.of(participanteId) : null,
                estado != null ? EstadoValidacion.valueOf(estado) : null,
                tipoDestino != null ? TipoDestino.valueOf(tipoDestino) : null,
                desde != null ? Instant.parse(desde) : null, hasta != null ? Instant.parse(hasta) : null,
                cursor != null ? Instant.parse(cursor) : null);
        return EvidenciaPageResponse.from(listarUseCase.listar(comando));
    }

    @RequiresPermission(Permission.MANAGE_EVIDENCE)
    @PostMapping("/{id}/review")
    public EvidenciaResponse revisar(@ActorAutenticado UserId actor, @PathVariable UUID id,
                                      @Valid @RequestBody RevisarManualmenteRequest request) {
        var evidencia = revisarUseCase.revisar(new RevisarManualmenteCommand(actor, EvidenciaId.of(id),
                request.aprobar(), request.notas()));
        return EvidenciaResponse.from(evidencia);
    }

    @RequiresPermission(Permission.MANAGE_EVIDENCE)
    @PostMapping("/{id}/void")
    public EvidenciaResponse anular(@ActorAutenticado UserId actor, @PathVariable UUID id,
                                     @Valid @RequestBody AnularVeredictoRequest request) {
        var evidencia = anularUseCase.anular(new AnularVeredictoCommand(actor, EvidenciaId.of(id),
                request.notas()));
        return EvidenciaResponse.from(evidencia);
    }
}
