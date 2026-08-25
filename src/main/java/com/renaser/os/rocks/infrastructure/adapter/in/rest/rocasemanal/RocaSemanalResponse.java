package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocasemanal;

import com.renaser.os.rocks.domain.model.rocasemanal.AccionCritica;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RocaSemanalResponse(UUID id, UUID rocaMaestraId, int numeroSemana, String titulo,
                                   List<String> accionesCriticas, String obstaculo, String contingencia,
                                   Integer autoevaluacionInicio, Integer autoevaluacionFin, String bloqueoPrincipal,
                                   String correccion, Instant creadoEn, Instant actualizadoEn) {

    public static RocaSemanalResponse from(RocaSemanal r) {
        List<String> acciones = r.acciones().stream()
                .sorted((a, b) -> Integer.compare(a.orden(), b.orden()))
                .map(AccionCritica::descripcion)
                .toList();
        return new RocaSemanalResponse(r.id().value(), r.rocaMaestraId().value(), r.numeroSemana(), r.titulo(),
                acciones, r.obstaculo(), r.contingencia(), r.autoevaluacionInicio(), r.autoevaluacionFin(),
                r.bloqueoPrincipal(), r.correccion(), r.creadoEn(), r.actualizadoEn());
    }
}
