package com.renaser.os.support.infrastructure.adapter.in.rest.ticketsoporte;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.application.ports.in.ticketsoporte.AbrirTicketSoporteUseCase;
import com.renaser.os.support.application.ports.in.ticketsoporte.AbrirTicketSoporteUseCase.AbrirTicketSoporteCommand;
import com.renaser.os.support.application.ports.in.ticketsoporte.ListarTicketsSoporteUseCase;
import com.renaser.os.support.application.ports.in.ticketsoporte.SolicitarUrlAdjuntoSoporteUseCase;
import com.renaser.os.support.application.ports.in.ticketsoporte.SolicitarUrlAdjuntoSoporteUseCase.SolicitarUrlAdjuntoCommand;
import com.renaser.os.support.domain.model.ticketsoporte.CategoriaSoporte;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/support-tickets")
public class TicketSoporteController {

    private final AbrirTicketSoporteUseCase abrirUseCase;
    private final ListarTicketsSoporteUseCase listarUseCase;
    private final SolicitarUrlAdjuntoSoporteUseCase solicitarUrlUseCase;

    public TicketSoporteController(AbrirTicketSoporteUseCase abrirUseCase, ListarTicketsSoporteUseCase listarUseCase,
                                    SolicitarUrlAdjuntoSoporteUseCase solicitarUrlUseCase) {
        this.abrirUseCase = abrirUseCase;
        this.listarUseCase = listarUseCase;
        this.solicitarUrlUseCase = solicitarUrlUseCase;
    }

    @PostMapping
    public ResponseEntity<TicketSoporteResponse> abrir(@RequestHeader("X-Actor-Id") String actorId,
                                                         @RequestBody @Valid AbrirTicketSoporteRequest request) {
        var command = new AbrirTicketSoporteCommand(UserId.of(actorId), parseCategoria(request.category()),
                request.subject(), request.message(), request.clientLog(), request.bucketEfectivo(),
                request.rutaEfectiva());
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketSoporteResponse.from(abrirUseCase.abrir(command)));
    }

    @GetMapping
    public List<TicketSoporteResponse> misTickets(@RequestHeader("X-Actor-Id") String actorId) {
        return listarUseCase.misTickets(UserId.of(actorId)).stream().map(TicketSoporteResponse::from).toList();
    }

    @PostMapping("/attachments/upload-url")
    public UrlAdjuntoResponse solicitarUrlAdjunto(@RequestHeader("X-Actor-Id") String actorId,
                                                    @RequestBody @Valid SolicitarUrlAdjuntoRequest request) {
        return UrlAdjuntoResponse.from(solicitarUrlUseCase.solicitar(new SolicitarUrlAdjuntoCommand(
                UserId.of(actorId), request.fileName(), request.contentType())));
    }

    /** category llega en ingles (wire viejo), el dominio la modela en espanol. null/blank = sin categoria (default OTRO en el servicio). */
    private static CategoriaSoporte parseCategoria(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        return switch (category) {
            case "TECHNICAL" -> CategoriaSoporte.TECNICO;
            case "ACCOUNT" -> CategoriaSoporte.CUENTA;
            case "PROGRAM" -> CategoriaSoporte.PROGRAMA;
            case "BILLING" -> CategoriaSoporte.FACTURACION;
            case "OTHER" -> CategoriaSoporte.OTRO;
            default -> throw new IllegalArgumentException("category invalida: " + category);
        };
    }
}
