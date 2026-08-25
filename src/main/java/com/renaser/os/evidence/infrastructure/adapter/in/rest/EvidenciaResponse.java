package com.renaser.os.evidence.infrastructure.adapter.in.rest;

import com.renaser.os.evidence.api.DestinoEvidencia;
import com.renaser.os.evidence.domain.model.evidencia.Evidencia;

import java.time.Instant;
import java.util.UUID;

/** Proyección explícita (CLAUDE.MD §5.4.2/§8) — nunca la entidad JPA ni el dominio serializados directo. */
public record EvidenciaResponse(UUID id, UUID participanteId, UUID registroHabitoId, UUID rocaDiariaId,
                                 UUID registroEspirituId, String tipo, String contenidoTexto, Instant timestampExif,
                                 Instant subidaEn, Double gpsLat, Double gpsLng, boolean esPrincipal,
                                 String estadoValidacion, String notasValidacion, int intentosIa,
                                 boolean penalizacionAplicada, boolean publicadaEnMuro) {

    public static EvidenciaResponse from(Evidencia e) {
        UUID registroHabitoId = e.destino() instanceof DestinoEvidencia.RegistroHabito h ? h.registroHabitoId() : null;
        UUID rocaDiariaId = e.destino() instanceof DestinoEvidencia.RocaDiaria r ? r.rocaDiariaId() : null;
        UUID registroEspirituId = e.destino() instanceof DestinoEvidencia.RegistroEspiritu s
                ? s.registroEspirituId() : null;
        return new EvidenciaResponse(e.id().value(), e.participanteId().value(), registroHabitoId, rocaDiariaId,
                registroEspirituId, e.tipo().name(), e.contenidoTexto(), e.timestampExif(), e.subidaEn(),
                e.gpsLat(), e.gpsLng(), e.esPrincipal(), e.estadoValidacion().name(), e.notasValidacion(),
                e.intentosIa(), e.penalizacionAplicada(), e.publicadaEnMuro());
    }
}
