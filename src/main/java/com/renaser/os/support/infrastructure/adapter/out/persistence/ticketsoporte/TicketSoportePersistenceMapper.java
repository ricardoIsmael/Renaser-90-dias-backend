package com.renaser.os.support.infrastructure.adapter.out.persistence.ticketsoporte;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.domain.model.ticketsoporte.AdjuntoSoporte;
import com.renaser.os.support.domain.model.ticketsoporte.CategoriaSoporte;
import com.renaser.os.support.domain.model.ticketsoporte.EstadoTicketSoporte;
import com.renaser.os.support.domain.model.ticketsoporte.TicketSoporte;
import com.renaser.os.support.domain.model.ticketsoporte.TicketSoporteId;
import org.springframework.stereotype.Component;

/** Traduccion a mano, no MapStruct — mismo criterio que TicketMentorPersistenceMapper. */
@Component
class TicketSoportePersistenceMapper {

    TicketSoporte toDomain(TicketSoporteJpaEntity e) {
        AdjuntoSoporte adjunto = e.getAdjuntoBucket() != null && e.getAdjuntoRuta() != null
                ? new AdjuntoSoporte(e.getAdjuntoBucket(), e.getAdjuntoRuta())
                : null;
        return TicketSoporte.rehydrate(
                TicketSoporteId.of(e.getId()),
                UserId.of(e.getUsuarioId()),
                toDomainCategoria(e.getCategoria()),
                e.getAsunto(),
                e.getMensaje(),
                e.getLogCliente(),
                adjunto,
                toDomainEstado(e.getEstado()),
                e.getNotasAdmin(),
                e.getResueltoEn(),
                e.getCreadoEn(),
                e.getActualizadoEn());
    }

    TicketSoporteJpaEntity toEntity(TicketSoporte t) {
        AdjuntoSoporte adjunto = t.adjunto();
        return new TicketSoporteJpaEntity(
                t.id().value(),
                t.usuarioId().value(),
                toJpaCategoria(t.categoria()),
                t.asunto(),
                t.mensaje(),
                t.logCliente(),
                adjunto == null ? null : adjunto.bucket(),
                adjunto == null ? null : adjunto.ruta(),
                toJpaEstado(t.estado()),
                t.notasAdmin(),
                t.resueltoEn(),
                t.creadoEn(),
                t.actualizadoEn());
    }

    private CategoriaSoporteJpa toJpaCategoria(CategoriaSoporte categoria) {
        return switch (categoria) {
            case TECNICO -> CategoriaSoporteJpa.TECNICO;
            case CUENTA -> CategoriaSoporteJpa.CUENTA;
            case PROGRAMA -> CategoriaSoporteJpa.PROGRAMA;
            case FACTURACION -> CategoriaSoporteJpa.FACTURACION;
            case OTRO -> CategoriaSoporteJpa.OTRO;
        };
    }

    private CategoriaSoporte toDomainCategoria(CategoriaSoporteJpa jpa) {
        return switch (jpa) {
            case TECNICO -> CategoriaSoporte.TECNICO;
            case CUENTA -> CategoriaSoporte.CUENTA;
            case PROGRAMA -> CategoriaSoporte.PROGRAMA;
            case FACTURACION -> CategoriaSoporte.FACTURACION;
            case OTRO -> CategoriaSoporte.OTRO;
        };
    }

    private EstadoTicketSoporteJpa toJpaEstado(EstadoTicketSoporte estado) {
        return switch (estado) {
            case ABIERTO -> EstadoTicketSoporteJpa.ABIERTO;
            case RESUELTO -> EstadoTicketSoporteJpa.RESUELTO;
        };
    }

    private EstadoTicketSoporte toDomainEstado(EstadoTicketSoporteJpa jpa) {
        return switch (jpa) {
            case ABIERTO -> EstadoTicketSoporte.ABIERTO;
            case RESUELTO -> EstadoTicketSoporte.RESUELTO;
        };
    }
}
