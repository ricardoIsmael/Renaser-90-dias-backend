package com.renaser.os.support.infrastructure.adapter.in.rest.ticketsoporte;

import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.support.application.ports.in.ticketsoporte.ListarTicketsSoporteUseCase;
import com.renaser.os.support.application.ports.in.ticketsoporte.ResolverTicketSoporteUseCase;
import com.renaser.os.support.application.ports.in.ticketsoporte.ResolverTicketSoporteUseCase.ResolverTicketSoporteCommand;
import com.renaser.os.support.domain.model.ticketsoporte.EstadoTicketSoporte;
import com.renaser.os.support.domain.model.ticketsoporte.TicketSoporteId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/support-tickets")
public class TicketSoporteAdminController {

    private final ListarTicketsSoporteUseCase listarUseCase;
    private final ResolverTicketSoporteUseCase resolverUseCase;

    public TicketSoporteAdminController(ListarTicketsSoporteUseCase listarUseCase,
                                         ResolverTicketSoporteUseCase resolverUseCase) {
        this.listarUseCase = listarUseCase;
        this.resolverUseCase = resolverUseCase;
    }

    @RequiresPermission(Permission.MANAGE_SUPPORT_TICKETS)
    @GetMapping
    public List<TicketSoporteResponse> todos(@ActorAutenticado UserId actor,
                                              @RequestParam(required = false) String status) {
        return listarUseCase.todos(actor, parseEstado(status)).stream()
                .map(TicketSoporteResponse::from).toList();
    }

    @RequiresPermission(Permission.MANAGE_SUPPORT_TICKETS)
    @PostMapping("/{id}/resolve")
    public TicketSoporteResponse resolver(@PathVariable UUID id, @ActorAutenticado UserId actor,
                                           @RequestBody(required = false) ResolverTicketSoporteRequest request) {
        String adminNotes = request != null ? request.adminNotes() : null;
        var command = new ResolverTicketSoporteCommand(TicketSoporteId.of(id), actor, adminNotes);
        return TicketSoporteResponse.from(resolverUseCase.resolver(command));
    }

    private static EstadoTicketSoporte parseEstado(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return switch (status) {
            case "OPEN" -> EstadoTicketSoporte.ABIERTO;
            case "RESOLVED" -> EstadoTicketSoporte.RESUELTO;
            default -> throw new IllegalArgumentException("status invalido: " + status);
        };
    }
}
