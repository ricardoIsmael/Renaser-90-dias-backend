package com.renaser.os.support.infrastructure.adapter.in.rest.ticketsoporte;

import com.renaser.os.support.application.ports.in.ticketsoporte.TicketSoporteVista;
import com.renaser.os.support.domain.model.ticketsoporte.CategoriaSoporte;
import com.renaser.os.support.domain.model.ticketsoporte.EstadoTicketSoporte;
import com.renaser.os.support.domain.model.ticketsoporte.TicketSoporte;

import java.time.Instant;

public record TicketSoporteResponse(
        String id,
        String userId,
        String category,
        String subject,
        String message,
        String clientLog,
        String attachmentUrl,
        String status,
        String adminNotes,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static TicketSoporteResponse from(TicketSoporteVista vista) {
        TicketSoporte t = vista.ticket();
        return new TicketSoporteResponse(
                t.id().value().toString(),
                t.usuarioId().value().toString(),
                toWireCategoria(t.categoria()),
                t.asunto(),
                t.mensaje(),
                t.logCliente(),
                vista.attachmentUrl() != null ? vista.attachmentUrl().toString() : null,
                toWireEstado(t.estado()),
                t.notasAdmin(),
                t.resueltoEn(),
                t.creadoEn(),
                t.actualizadoEn());
    }

    private static String toWireCategoria(CategoriaSoporte categoria) {
        return switch (categoria) {
            case TECNICO -> "TECHNICAL";
            case CUENTA -> "ACCOUNT";
            case PROGRAMA -> "PROGRAM";
            case FACTURACION -> "BILLING";
            case OTRO -> "OTHER";
        };
    }

    private static String toWireEstado(EstadoTicketSoporte estado) {
        return switch (estado) {
            case ABIERTO -> "OPEN";
            case RESUELTO -> "RESOLVED";
        };
    }
}
