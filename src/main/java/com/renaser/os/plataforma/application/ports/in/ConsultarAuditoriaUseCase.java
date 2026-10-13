package com.renaser.os.plataforma.application.ports.in;

import com.renaser.os.plataforma.domain.model.auditoria.TipoDeCambio;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** La auditoría paginada, con filtros (contrato §6, {@code GET /soporte-ti/auditoria}). */
public interface ConsultarAuditoriaUseCase {

    PaginaDeAuditoria consultar(AccesoDeConsola acceso, Filtro filtro);

    /** Todos opcionales. {@code tamano} se acota a 1..100. */
    record Filtro(String clave, UserId actor, Instant desde, Instant hasta, int pagina, int tamano) {
    }

    record PaginaDeAuditoria(List<Fila> contenido, int pagina, long total) {
    }

    /** @param actor {@code null} en un cambio automático */
    record Fila(UUID id, TipoDeCambio tipo, FuncionalidadEnConsola.Actor actor, String clave, Map<String, Object> antes,
                Map<String, Object> despues, String motivo, String dispositivo, Instant creadoEn) {
    }
}
