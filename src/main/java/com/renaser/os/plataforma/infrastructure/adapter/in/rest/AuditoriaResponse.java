package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import com.renaser.os.plataforma.application.ports.in.ConsultarAuditoriaUseCase.PaginaDeAuditoria;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** {@code GET /soporte-ti/auditoria} (contrato §6): {@code { contenido, pagina, total }}. */
record AuditoriaResponse(List<Cambio> contenido, int pagina, long total) {

    static AuditoriaResponse from(PaginaDeAuditoria pagina) {
        return new AuditoriaResponse(pagina.contenido().stream().map(fila -> new Cambio(fila.id().toString(),
                fila.tipo().name(), FuncionalidadEnConsolaResponse.ActorResponse.from(fila.actor()), fila.clave(),
                fila.antes(), fila.despues(), fila.motivo(), fila.dispositivo(), fila.creadoEn())).toList(),
                pagina.pagina(), pagina.total());
    }

    record Cambio(String id, String tipo, FuncionalidadEnConsolaResponse.ActorResponse actor, String clave,
                  Map<String, Object> antes, Map<String, Object> despues, String motivo, String dispositivo,
                  Instant creadoEn) {
    }
}
