package com.renaser.os.evidence.infrastructure.adapter.in.rest;

import com.renaser.os.evidence.api.DestinoEvidencia;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase.EvidenciaListada;
import com.renaser.os.evidence.domain.model.evidencia.Evidencia;

import java.time.Instant;
import java.util.UUID;

/**
 * Proyección explícita (CLAUDE.MD §5.4.2/§8) — nunca la entidad JPA ni el dominio serializados directo.
 *
 * <p>{@code fotoUrl} (D-252, 2026-10-05): la URL de lectura firmada de la foto, para que Yo muestre la
 * foto real en cada miniatura. Solo viene resuelta en el listado de la app ({@code GET /api/v1/evidence}),
 * que es la única pantalla que la pinta; en el detalle, en el panel admin y en las respuestas de revisar
 * o anular viaja {@code null} — igual que el {@code mediaUrl} del chat, que solo viene en el listado de
 * mensajes. {@code null} también cuando la evidencia no es una foto ({@code Evidencia#tieneFoto}).
 * Es un campo nuevo al final: los APK publicados lo ignoran (su esquema es {@code passthrough}). Nunca se
 * exponen el bucket ni la ruta del objeto como campos propios.
 */
public record EvidenciaResponse(UUID id, UUID participanteId, UUID registroHabitoId, UUID rocaDiariaId,
                                 UUID registroEspirituId, String tipo, String contenidoTexto, Instant timestampExif,
                                 Instant subidaEn, Double gpsLat, Double gpsLng, boolean esPrincipal,
                                 String estadoValidacion, String notasValidacion, int intentosIa,
                                 boolean penalizacionAplicada, boolean publicadaEnMuro, String fotoUrl) {

    public static EvidenciaResponse from(Evidencia e) {
        return from(e, null);
    }

    public static EvidenciaResponse from(EvidenciaListada fila) {
        return from(fila.evidencia(), fila.fotoUrl());
    }

    private static EvidenciaResponse from(Evidencia e, String fotoUrl) {
        UUID registroHabitoId = e.destino() instanceof DestinoEvidencia.RegistroHabito h ? h.registroHabitoId() : null;
        UUID rocaDiariaId = e.destino() instanceof DestinoEvidencia.RocaDiaria r ? r.rocaDiariaId() : null;
        UUID registroEspirituId = e.destino() instanceof DestinoEvidencia.RegistroEspiritu s
                ? s.registroEspirituId() : null;
        return new EvidenciaResponse(e.id().value(), e.participanteId().value(), registroHabitoId, rocaDiariaId,
                registroEspirituId, e.tipo().name(), e.contenidoTexto(), e.timestampExif(), e.subidaEn(),
                e.gpsLat(), e.gpsLng(), e.esPrincipal(), e.estadoValidacion().name(), e.notasValidacion(),
                e.intentosIa(), e.penalizacionAplicada(), e.publicadaEnMuro(), fotoUrl);
    }
}
